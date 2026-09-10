package com.hm.manage.service.governance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hm.common.exception.ServiceException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.quartz.CronExpression;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Cron submits immutable original Kettle XML. It never substitutes fixed JSON batches. */
@Service
public class DataGovernanceKettleScheduler
{
    public record Input(String name, Long revision, Long definitionRevision, String cron, String timeZone) { }
    public record StateInput(Boolean enabled, Long revision) { }
    public record View(String id, String name, long revision, String definitionId, long definitionRevision,
        String cron, String timeZone, boolean enabled, String status, String activeRunId, String lastRunId,
        String lastRunState, String nextRunAt, String lastError, String xmlSha256, String inputsHash,
        boolean recoveryRequired, String createdAt, String updatedAt) { }
    public static class Schedule
    {
        public String id, name, definitionId, snapshotId, cron, timeZone, status, activeRunId, lastRunId;
        public String lastRunState, nextRunAt, lastError, xmlSha256, inputsHash, createdAt, updatedAt;
        public long owner, revision, definitionRevision;
        public boolean enabled, recoveryRequired;
    }
    private static final Set<String> SUCCEEDED = Set.of("SUCCEEDED", "PREVIEW_COMPLETE");
    private static final Set<String> FAILED = Set.of("FAILED", "STOPPED", "TIMED_OUT", "VALIDATION_FAILED", "PREPARATION_FAILED", "SUBMISSION_REJECTED");
    private static final Set<String> UNCERTAIN = Set.of("SUBMISSION_UNKNOWN", "RECOVERY_REQUIRED", "SUBMITTING");
    private final DataGovernanceKettleService service;
    private final DataGovernanceKettleProperties properties;
    private final Clock clock;
    private final Executor executor;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Set<String> inFlight = new HashSet<>();
    private Path root;
    private FileChannel channel;
    private FileLock lock;
    private ScheduledExecutorService timer;
    private boolean closed;
    private String coordinatorError;

    @Autowired public DataGovernanceKettleScheduler(DataGovernanceKettleService service, DataGovernanceKettleProperties properties)
    { this(service, properties, Clock.systemUTC(), Executors.newFixedThreadPool(4, r -> { Thread t = new Thread(r, "kettle-schedule-operation"); t.setDaemon(true); return t; })); }
    DataGovernanceKettleScheduler(DataGovernanceKettleService service, DataGovernanceKettleProperties properties, Clock clock, Executor executor)
    { this.service = service; this.properties = properties; this.clock = clock; this.executor = executor; }

    @PostConstruct public synchronized void start()
    {
        if (closed || timer != null || !properties.isEnabled()) return;
        try { initialize(); }
        catch (ServiceException e) { coordinatorError = "原生计划存储不可用，未启动自动触发"; return; }
        timer = Executors.newSingleThreadScheduledExecutor(r -> { Thread t = new Thread(r, "kettle-schedule-tick"); t.setDaemon(true); return t; });
        timer.scheduleWithFixedDelay(() -> { try { tick(); } catch (Exception e) { synchronized (this) { coordinatorError = "原生计划协调器暂停：请核查私有元数据存储"; } } }, 1, 1, TimeUnit.SECONDS);
    }
    private synchronized void initialize()
    {
        if (closed) reject("原生计划协调器已关闭");
        if (coordinatorError != null) reject(coordinatorError);
        if (root != null) return;
        try
        {
            Path candidate = service.scheduleDirectory(); Path lockPath = candidate.resolve(".scheduler.lock");
            if (Files.isSymbolicLink(lockPath)) reject("计划存储锁无效");
            channel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
            lock = channel.tryLock(); if (lock == null) reject("原生计划协调器已运行");
            root = candidate;
            for (Schedule s : stored())
            {
                if (s.activeRunId != null || s.recoveryRequired)
                    pauseForRecovery(s, "RECOVERY_REQUIRED", "服务已重启，须核查已有运行；不会自动重发");
                else if (s.enabled)
                {
                    s.nextRunAt = next(s.cron, s.timeZone, clock.instant());
                    s.enabled = s.nextRunAt != null; s.status = s.enabled ? "READY" : "EXHAUSTED";
                }
                save(s);
            }
        }
        catch (Exception e)
        { root = null; try { if (lock != null) lock.release(); if (channel != null) channel.close(); } catch (IOException ignored) { }
          throw new ServiceException("原生计划存储不可用或协调器已占用"); }
    }
    public synchronized List<View> list(String definitionId, long owner)
    {
        initialize(); service.definitionSummary(definitionId, owner);
        return stored().stream().filter(s -> s.owner == owner && definitionId.equals(s.definitionId))
            .sorted(Comparator.comparing((Schedule s) -> Instant.parse(s.createdAt)).reversed()).map(this::view).toList();
    }
    public synchronized View create(String definitionId, Input input, long owner)
    {
        initialize(); service.definitionSummary(definitionId, owner);
        if (stored().stream().filter(s -> s.owner == owner).count() >= 100) reject("当前用户最多 100 个原生计划");
        Schedule s = new Schedule(); s.id = UUID.randomUUID().toString(); s.owner = owner; s.definitionId = definitionId;
        s.revision = 1; s.createdAt = clock.instant().toString(); apply(s, input, owner); save(s); return view(s);
    }
    public synchronized View update(String id, Input input, long owner)
    {
        initialize(); Schedule s = owned(id, owner); revision(s, input == null ? null : input.revision());
        idle(s); apply(s, input, owner); s.revision++; save(s); return view(s);
    }
    private void apply(Schedule s, Input input, long owner)
    {
        if (input == null || input.definitionRevision() == null) reject("请提供当前原生定义修订");
        if (input.name() == null || input.name().isBlank() || input.name().length() > 80) reject("计划名称须为 1 至 80 字");
        if (next(input.cron(), input.timeZone(), clock.instant()) == null) reject("该 Cron 没有后续执行时间");
        var snapshot = service.captureFrozen(s.definitionId, input.definitionRevision(), owner);
        String snapshotId = UUID.randomUUID().toString(); service.writeFrozen(snapshotId, snapshot);
        s.name = input.name().trim(); s.definitionRevision = snapshot.definitionRevision; s.snapshotId = snapshotId;
        s.xmlSha256 = snapshot.xmlSha256; s.inputsHash = snapshot.inputsHash; s.cron = input.cron().trim(); s.timeZone = input.timeZone();
        s.enabled = false; s.status = "PAUSED"; s.nextRunAt = null; s.lastError = null; s.recoveryRequired = false;
    }
    public synchronized View state(String id, StateInput input, long owner)
    {
        initialize(); Schedule s = owned(id, owner);
        if (input == null || input.enabled() == null) reject("请提供启停状态"); revision(s, input.revision());
        if (input.enabled())
        {
            requireWorker(); idle(s); s.nextRunAt = next(s.cron, s.timeZone, clock.instant());
            if (s.nextRunAt == null) reject("该 Cron 没有后续执行时间"); s.enabled = true; s.status = "READY"; s.lastError = null;
        }
        else { s.enabled = false; s.nextRunAt = null; if (s.activeRunId == null && !s.recoveryRequired && !FAILED.contains(s.status)) s.status = "PAUSED"; }
        s.revision++; save(s); return view(s);
    }
    public synchronized View runNow(String id, long owner)
    {
        initialize(); Schedule s = owned(id, owner); requireWorker(); idle(s); trigger(s); return view(read(s.id));
    }
    public View recover(String id, long owner)
    {
        String runId;
        synchronized (this)
        {
            initialize(); Schedule s = owned(id, owner); requireWorker();
            if (inFlight.contains(id)) reject("该计划正在提交或核查，请稍后刷新");
            if (s.activeRunId == null) return view(s);
            runId = s.activeRunId; s.enabled = false; s.nextRunAt = null; save(s); inFlight.add(id);
        }
        try
        {
            Map<String,Object> result = service.run(runId, owner);
            synchronized (this) { Schedule s = owned(id, owner); if (runId.equals(s.activeRunId)) observe(s, result, true); return view(s); }
        }
        catch (ServiceException e)
        { synchronized (this) { Schedule s = owned(id, owner); pauseForRecovery(s, "RECOVERY_REQUIRED", "未取得原运行记录，保留恢复屏障；禁止重新提交"); save(s); return view(s); } }
        finally { synchronized (this) { inFlight.remove(id); } }
    }
    synchronized void tick()
    {
        if (closed || coordinatorError != null || !properties.isEnabled()) return;
        initialize(); Instant now = clock.instant();
        for (Schedule s : stored())
        {
            if (s.recoveryRequired || inFlight.contains(s.id)) continue;
            if (s.activeRunId != null)
            {
                if (s.enabled && s.nextRunAt != null && !Instant.parse(s.nextRunAt).isAfter(now)) { s.nextRunAt = next(s.cron, s.timeZone, now); save(s); }
                poll(s); continue;
            }
            if (!s.enabled || s.nextRunAt == null || Instant.parse(s.nextRunAt).isAfter(now)) continue;
            // A missed full tick window is skipped, never replayed or queued as a catch-up burst.
            if (Duration.between(Instant.parse(s.nextRunAt), now).toMillis() >= 1000)
            { s.nextRunAt = next(s.cron, s.timeZone, now); if (s.nextRunAt == null) { s.enabled = false; s.status = "EXHAUSTED"; } save(s); continue; }
            trigger(s);
        }
    }
    private void trigger(Schedule s)
    {
        idle(s); String runId = UUID.randomUUID().toString(); s.activeRunId = runId; s.lastRunId = runId;
        s.lastRunState = "PREPARING"; s.status = "PREPARING"; s.lastError = null;
        s.nextRunAt = s.enabled ? next(s.cron, s.timeZone, clock.instant()) : null;
        save(s); // The durable run intent and next due time are atomic before any worker submission.
        String id = s.id, snapshotId = s.snapshotId; long owner = s.owner; inFlight.add(id);
        dispatch(id, () -> {
            var snapshot = service.readFrozen(snapshotId, owner);
            Map<String,Object> result = service.submitFrozen(snapshot, runId, owner);
            synchronized (this) { if (closed) return; Schedule current = owned(id, owner); if (runId.equals(current.activeRunId)) observe(current, result, false); }
        });
    }
    private void poll(Schedule s)
    {
        String id = s.id, runId = s.activeRunId; long owner = s.owner; inFlight.add(id);
        dispatch(id, () -> {
            Map<String,Object> result = service.run(runId, owner);
            synchronized (this) { if (closed) return; Schedule current = owned(id, owner); if (runId.equals(current.activeRunId)) observe(current, result, true); }
        });
    }
    private void dispatch(String id, Runnable operation)
    {
        try
        {
            executor.execute(() -> {
                try { synchronized (this) { if (closed) return; } operation.run(); }
                catch (Exception e) { synchronized (this) { if (!closed) { Schedule current = read(id); pauseForRecovery(current, "RECOVERY_REQUIRED", "提交或核查中断，须核查原运行；不会自动重发"); save(current); } } }
                finally { synchronized (this) { inFlight.remove(id); } }
            });
        }
        catch (RuntimeException e) { inFlight.remove(id); Schedule current = read(id); pauseForRecovery(current, "RECOVERY_REQUIRED", "执行队列未确认接收，保留原运行意图"); save(current); }
    }
    private void observe(Schedule s, Map<String,Object> result, boolean queried)
    {
        String state = String.valueOf(result.getOrDefault("state", "SUBMISSION_UNKNOWN")); s.lastRunState = state;
        boolean localFailure = Set.of("PREPARATION_FAILED", "VALIDATION_FAILED", "SUBMISSION_REJECTED").contains(state);
        if (UNCERTAIN.contains(state) || queried && !Boolean.TRUE.equals(result.get("workerStateAvailable")) && !localFailure)
        { pauseForRecovery(s, UNCERTAIN.contains(state) ? state : "RECOVERY_REQUIRED", "原运行状态未确认，计划暂停；恢复只核查此运行"); }
        // A native terminal event can precede JVM cleanup. Keep the overlap barrier until process exit is confirmed.
        else if (!localFailure && (SUCCEEDED.contains(state) || FAILED.contains(state))
            && !result.containsKey("exitCode") && !result.containsKey("finishedAt"))
        { s.status = "FINISHING"; s.recoveryRequired = false; }
        else if (SUCCEEDED.contains(state))
        {
            s.activeRunId = null; s.recoveryRequired = false; s.lastError = null;
            s.nextRunAt = s.enabled ? next(s.cron, s.timeZone, clock.instant()) : null;
            if (s.enabled && s.nextRunAt == null) s.enabled = false;
            s.status = s.enabled ? "READY" : "PAUSED";
        }
        else if (FAILED.contains(state))
        { s.activeRunId = null; s.recoveryRequired = false; s.enabled = false; s.nextRunAt = null; s.status = state; s.lastError = "上次原生运行未成功，计划已暂停；不会自动重试"; }
        else { s.status = state; s.recoveryRequired = false; }
        save(s);
    }
    private void pauseForRecovery(Schedule s, String status, String error)
    { s.enabled = false; s.nextRunAt = null; s.recoveryRequired = true; s.status = status; s.lastError = error; }
    private void idle(Schedule s)
    { if (s.activeRunId != null || s.recoveryRequired || inFlight.contains(s.id)) reject("该计划仍有运行或恢复屏障，禁止重叠执行或修改冻结版本"); }
    private Schedule owned(String id, long owner)
    { Schedule s = read(id); if (s.owner != owner) throw new ServiceException("计划不存在或无权访问", 404); return s; }
    private Schedule read(String id)
    {
        if (id == null || !id.matches("[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}")) throw new ServiceException("计划不存在或无权访问", 404);
        Path file = root.resolve(id + ".json");
        try { if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) throw new ServiceException("计划不存在或无权访问", 404);
            if (Files.size(file) > 65536) reject("计划元数据无效"); return mapper.readValue(Files.readAllBytes(file), Schedule.class); }
        catch (ServiceException e) { throw e; } catch (Exception e) { throw new ServiceException("计划元数据读取失败"); }
    }
    private List<Schedule> stored()
    {
        try (var files = Files.newDirectoryStream(root, "*.json"))
        { List<Schedule> result = new ArrayList<>(); for (Path file : files) result.add(read(file.getFileName().toString().replaceFirst("\\.json$", ""))); return result; }
        catch (IOException e) { throw new ServiceException("计划列表读取失败"); }
    }
    private void save(Schedule s)
    {
        if (closed) reject("原生计划协调器已关闭"); s.updatedAt = clock.instant().toString();
        Path target = root.resolve(s.id + ".json"), temporary = root.resolve(s.id + "." + UUID.randomUUID() + ".tmp");
        try
        {
            if (Files.isSymbolicLink(target)) reject("计划文件不能为符号链接"); byte[] bytes = mapper.writeValueAsBytes(s);
            try (FileChannel output = FileChannel.open(temporary, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE))
            { try { Files.setPosixFilePermissions(temporary, PosixFilePermissions.fromString("rw-------")); } catch (UnsupportedOperationException ignored) { }
              ByteBuffer buffer = ByteBuffer.wrap(bytes); while (buffer.hasRemaining()) output.write(buffer); output.force(true); }
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        }
        catch (ServiceException e) { throw e; } catch (Exception e) { throw new ServiceException("计划元数据保存失败"); }
        finally { try { Files.deleteIfExists(temporary); } catch (IOException ignored) { } }
    }
    private View view(Schedule s)
    { return new View(s.id, s.name, s.revision, s.definitionId, s.definitionRevision, s.cron, s.timeZone, s.enabled, s.status,
        s.activeRunId, s.lastRunId, s.lastRunState, s.nextRunAt, s.lastError, s.xmlSha256, s.inputsHash, s.recoveryRequired, s.createdAt, s.updatedAt); }
    static String next(String cron, String zone, Instant after)
    {
        if (cron == null || cron.isBlank() || cron.length() > 120 || zone == null || !ZoneId.getAvailableZoneIds().contains(zone)) reject("请提供有效 Quartz Cron 和 IANA 时区");
        try { CronExpression expression = new CronExpression(cron.trim()); expression.setTimeZone(TimeZone.getTimeZone(ZoneId.of(zone)));
            Date date = expression.getNextValidTimeAfter(Date.from(after)); return date == null ? null : date.toInstant().toString(); }
        catch (Exception e) { throw new ServiceException("Quartz Cron 表达式无效"); }
    }
    private void requireWorker() { if (!properties.isEnabled()) reject("原生 worker 未启用，计划只能保存为暂停状态"); }
    private static void revision(Schedule s, Long revision) { if (revision == null || revision != s.revision) throw new ServiceException("计划修订已变化，请刷新", 409); }
    private static void reject(String text) { throw new ServiceException(text); }
    @PreDestroy public synchronized void close()
    {
        closed = true; if (timer != null) timer.shutdownNow(); if (executor instanceof ExecutorService pool) pool.shutdownNow();
        try { if (lock != null) lock.release(); if (channel != null) channel.close(); } catch (IOException ignored) { }
    }
}
