package com.hm.manage.service.governance;

import com.hm.common.exception.ServiceException;
import com.hm.manage.service.governance.DataGovernanceModels.*;
import com.hm.manage.service.governance.DataGovernanceScheduleModels.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.quartz.CronExpression;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Cron coordinates durable fixed batches; it does not invoke RuoYi reflection jobs or mutate live flows. */
@Service
public class DataGovernanceScheduler
{
    private static final Set<String> ACTIVE = Set.of("QUEUED", "RUNNING");
    private static final String MODE = "FIXED_JSON_BATCH";
    private final DataGovernanceService service;
    private final DataGovernanceScheduleStore store;
    private final DataGovernanceNifiClient client;
    private final Clock clock;
    private ScheduledExecutorService timer;
    private boolean initialized;
    private boolean closed;
    private String coordinatorError;

    @Autowired
    public DataGovernanceScheduler(DataGovernanceService service, DataGovernanceScheduleStore store, DataGovernanceNifiClient client)
    { this(service, store, client, Clock.systemUTC()); }
    DataGovernanceScheduler(DataGovernanceService service, DataGovernanceScheduleStore store, DataGovernanceNifiClient client, Clock clock)
    { this.service = service; this.store = store; this.client = client; this.clock = clock; }

    @PostConstruct public synchronized void start()
    {
        // The default installation performs no storage scan, timer work, or NiFi requests.
        if (closed || timer != null || !client.configured()) return;
        try { initialize(); }
        catch (Exception e)
        {
            coordinatorError = "调度模块初始化失败：请核对私有任务存储并重启模块所在服务";
            return; // A governance storage problem must not prevent the rest of the platform from starting.
        }
        timer = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "data-governance-schedules"); thread.setDaemon(true); return thread;
        });
        timer.scheduleWithFixedDelay(() -> {
            try { tick(); }
            catch (Exception e)
            {
                synchronized (this) { coordinatorError = "调度协调器已停止触发：任务记录读取或保存失败，请管理员检查存储并重启服务"; }
            }
        }, 1, 1, TimeUnit.SECONDS);
    }
    private void initialize()
    {
        if (closed) throw new ServiceException("任务调度已关闭");
        if (coordinatorError != null) throw new ServiceException(coordinatorError);
        if (initialized) return;
        Instant now = clock.instant();
        for (Schedule schedule : store.schedules())
        {
            if (schedule.activeRunId != null || schedule.recoveryRequired)
            {
                schedule.enabled = false; schedule.recoveryRequired = true; schedule.nextRunAt = null;
                schedule.status = "RECOVERY_REQUIRED";
                schedule.lastError = "服务中断了任务跟踪；请恢复并确认上次执行资源已清理后再启用";
            }
            else if (schedule.enabled)
            {
                // No catch-up: discard pre-restart due time and calculate strictly after this start.
                schedule.nextRunAt = next(schedule.cron, schedule.timeZone, now);
                schedule.enabled = schedule.nextRunAt != null;
                schedule.status = schedule.enabled ? "READY" : "EXHAUSTED";
            }
            schedule.updatedAt = now.toString(); store.saveSchedule(schedule);
        }
        initialized = true;
    }
    private void requireEngine()
    { if (!client.configured()) throw new ServiceException("请先配置独立 NiFi 引擎，固定批次任务暂不可执行"); }

    public synchronized List<ReleaseSummary> releases(long owner, String flowId)
    {
        initialize(); if (flowId != null) DataGovernanceEngine.id(flowId);
        List<ReleaseSummary> result = new ArrayList<>();
        store.visitReleases(release -> {
            if (release.ownerId == owner && (flowId == null || flowId.equals(release.snapshot.run.flowId))) result.add(summary(release));
        });
        result.sort(Comparator.comparing(ReleaseSummary::createdAt).reversed());
        return result;
    }
    public synchronized ReleaseDetail release(String id, long owner)
    {
        initialize(); Release release = ownedRelease(id, owner);
        return new ReleaseDetail(summary(release), release.snapshot.inputJson, release.snapshot.parameters);
    }
    public synchronized ReleaseSummary publish(PublishRequest request, long owner)
    {
        initialize(); requireEngine();
        if (request == null) throw new ServiceException("发布参数不能为空");
        String name = name(request.name());
        int[] countAndVersion = {0, 0};
        store.visitReleases(release -> {
            if (release.ownerId == owner)
            {
                countAndVersion[0]++;
                if (release.snapshot.run.flowId.equals(request.flowId())) countAndVersion[1] = Math.max(countAndVersion[1], release.version);
            }
        });
        if (countAndVersion[0] >= 500) throw new ServiceException("当前用户已达到 500 个已发布版本上限");
        StoredRun snapshot;
        try { snapshot = service.prepareSnapshot(request.flowId(), new TestInput(request.inputJson(), request.parameters()), owner); }
        catch (DataGovernanceSafeFlow.UnsupportedFlow e) { throw new ServiceException(e.getMessage()); }
        Release release = new Release(); release.id = UUID.randomUUID().toString(); release.ownerId = owner;
        release.name = name; release.createdAt = clock.instant().toString(); release.snapshot = snapshot;
        release.version = countAndVersion[1] + 1;
        store.createRelease(release); return summary(release);
    }
    public synchronized List<ScheduleSummary> schedules(long owner, String flowId)
    {
        initialize(); if (flowId != null) DataGovernanceEngine.id(flowId);
        return store.schedules().stream().filter(s -> s.ownerId == owner && (flowId == null || flowId.equals(s.flowId)))
            .sorted(Comparator.comparing((Schedule s) -> s.createdAt).reversed()).map(this::summary).toList();
    }
    public synchronized ScheduleSummary create(ScheduleRequest request, long owner)
    {
        initialize();
        if (store.schedules().stream().filter(s -> s.ownerId == owner).count() >= 100)
            throw new ServiceException("当前用户已达到 100 个定时任务上限");
        Schedule schedule = new Schedule(); schedule.id = UUID.randomUUID().toString(); schedule.ownerId = owner;
        schedule.createdAt = clock.instant().toString(); apply(schedule, request, owner);
        schedule.revision = 1; save(schedule); return summary(schedule);
    }
    public synchronized ScheduleSummary update(String id, ScheduleRequest request, long owner)
    {
        initialize(); Schedule schedule = ownedSchedule(id, owner);
        revision(schedule, request == null ? null : request.revision());
        if (schedule.activeRunId != null || schedule.recoveryRequired) throw new ServiceException("执行中或待恢复的任务不能修改版本和计划");
        apply(schedule, request, owner); schedule.revision++; save(schedule); return summary(schedule);
    }
    private void apply(Schedule schedule, ScheduleRequest request, long owner)
    {
        if (request == null) throw new ServiceException("任务参数不能为空");
        Release release = ownedRelease(request.releaseId(), owner);
        schedule.name = name(request.name());
        String upcoming = next(request.cron(), request.timeZone(), clock.instant());
        if (upcoming == null) throw new ServiceException("Cron 在指定时区没有后续执行时间");
        schedule.cron = request.cron().trim(); schedule.timeZone = request.timeZone();
        schedule.releaseId = release.id; schedule.flowId = release.snapshot.run.flowId;
        // Editing is an explicit new configuration; require the operator to enable it again.
        schedule.enabled = false; schedule.nextRunAt = null; schedule.status = "PAUSED";
    }
    public synchronized ScheduleSummary state(String id, StateRequest request, long owner)
    {
        initialize(); Schedule schedule = ownedSchedule(id, owner);
        if (request == null || request.enabled() == null) throw new ServiceException("启停状态不能为空");
        revision(schedule, request.revision());
        if (request.enabled())
        {
            requireEngine();
            if (schedule.recoveryRequired) throw new ServiceException("请先恢复任务并确认清理，再启用定时触发");
            ownedRelease(schedule.releaseId, owner);
            schedule.nextRunAt = next(schedule.cron, schedule.timeZone, clock.instant());
            if (schedule.nextRunAt == null) throw new ServiceException("Cron 已没有后续执行时间，请修改计划");
            schedule.enabled = true; if (schedule.activeRunId == null) schedule.status = "READY";
        }
        else
        {
            schedule.enabled = false; schedule.nextRunAt = null;
            if (schedule.activeRunId == null && !schedule.recoveryRequired) schedule.status = "PAUSED";
        }
        schedule.revision++; save(schedule); return summary(schedule);
    }
    public synchronized TestRun runNow(String id, long owner)
    {
        initialize(); requireEngine(); Schedule schedule = ownedSchedule(id, owner);
        if (schedule.recoveryRequired) throw new ServiceException("请先恢复任务并确认资源已清理");
        if (schedule.activeRunId != null)
        {
            skip(schedule, clock.instant(), "手动触发时上次执行尚未结束");
            throw new ServiceException("该任务正在执行，本次触发已跳过");
        }
        return trigger(schedule, clock.instant());
    }
    /** Explicit recovery is the only path out of interrupted or unconfirmed-cleanup state. */
    public synchronized ScheduleSummary recover(String id, long owner)
    {
        initialize(); requireEngine(); Schedule schedule = ownedSchedule(id, owner);
        if (!schedule.recoveryRequired) return summary(schedule);
        if (schedule.activeRunId != null)
        {
            TestRun run;
            try { run = service.run(schedule.activeRunId, owner); }
            catch (ServiceException e)
            {
                // Submission always persists before creating any NiFi group. Missing means interrupted before submission.
                if (!"测试记录不存在或无权访问".equals(e.getMessage())) throw e;
                schedule.lastRunStatus = "INTERRUPTED";
                schedule.lastError = "服务在保存执行记录前中断，未提交引擎；任务已恢复为暂停";
                finishRecovery(schedule); return summary(schedule);
            }
            if (ACTIVE.contains(run.status)) throw new ServiceException("上次执行尚未结束，请先在测试记录中取消并等待清理");
            if (!run.cleanupConfirmed) run = service.cancel(run.id, owner);
            if (!run.cleanupConfirmed || ACTIVE.contains(run.status)) throw new ServiceException("尚未确认引擎资源清理，任务保持暂停");
            schedule.lastRunStatus = run.status; schedule.lastFinishedAt = run.updatedAt;
            schedule.lastError = run.error;
        }
        finishRecovery(schedule); return summary(schedule);
    }
    private void finishRecovery(Schedule schedule)
    {
        schedule.activeRunId = null; schedule.recoveryRequired = false; schedule.enabled = false;
        schedule.nextRunAt = null; schedule.status = "PAUSED"; schedule.revision++; save(schedule);
    }

    /** Package visibility makes time and run completion testable without waiting for cron timers. */
    synchronized void tick()
    {
        if (closed || !client.configured() || coordinatorError != null) return;
        initialize(); Instant now = clock.instant();
        for (Schedule schedule : store.schedules())
        {
            if (schedule.activeRunId != null && !schedule.recoveryRequired) reconcile(schedule);
            if (!schedule.enabled || schedule.recoveryRequired || schedule.nextRunAt == null || Instant.parse(schedule.nextRunAt).isAfter(now)) continue;
            // Move the deadline before submission. A restart or delayed poll never repeats a due occurrence.
            schedule.nextRunAt = next(schedule.cron, schedule.timeZone, now);
            if (schedule.nextRunAt == null) schedule.enabled = false;
            if (schedule.activeRunId != null) skip(schedule, now, "定时触发时上次执行尚未结束");
            else
            {
                try { trigger(schedule, now); }
                catch (ServiceException e)
                {
                    // A failed engine submission pauses that plan, not unrelated plans. Storage failures
                    // that prevented its recovery barrier still stop the entire coordinator fail-closed.
                    Schedule persisted = store.schedule(schedule.id);
                    if (persisted == null || !persisted.recoveryRequired) throw e;
                }
            }
        }
    }
    private TestRun trigger(Schedule schedule, Instant now)
    {
        Release release = ownedRelease(schedule.releaseId, schedule.ownerId);
        schedule.activeRunId = UUID.randomUUID().toString(); schedule.lastRunId = schedule.activeRunId;
        schedule.lastRunAt = now.toString(); schedule.lastFinishedAt = null; schedule.lastError = null;
        schedule.status = "STARTING"; schedule.lastRunStatus = "STARTING";
        save(schedule); // Durable intent BEFORE service submission, including its final run id.
        try
        {
            TestRun run = service.submitFrozen(release.snapshot, schedule.ownerId, schedule.activeRunId);
            observe(schedule, run); return run;
        }
        catch (Exception e)
        {
            schedule.enabled = false; schedule.nextRunAt = null; schedule.recoveryRequired = true;
            schedule.status = "RECOVERY_REQUIRED"; schedule.lastRunStatus = "SUBMISSION_UNCERTAIN";
            schedule.lastError = "任务提交未完成，请恢复任务并检查真实执行记录后再运行"; save(schedule);
            throw new ServiceException(schedule.lastError);
        }
    }
    private void reconcile(Schedule schedule)
    {
        try { observe(schedule, service.run(schedule.activeRunId, schedule.ownerId)); }
        catch (ServiceException e)
        {
            schedule.enabled = false; schedule.nextRunAt = null; schedule.recoveryRequired = true;
            schedule.status = "RECOVERY_REQUIRED"; schedule.lastError = "执行记录无法确认，任务已暂停，请恢复后再运行"; save(schedule);
        }
    }
    private void observe(Schedule schedule, TestRun run)
    {
        if (ACTIVE.contains(run.status))
        {
            if (!run.status.equals(schedule.lastRunStatus))
            { schedule.status = run.status; schedule.lastRunStatus = run.status; save(schedule); }
            return;
        }
        schedule.lastRunStatus = run.status; schedule.lastError = run.error; schedule.lastFinishedAt = run.updatedAt;
        if (!run.cleanupConfirmed)
        {
            schedule.enabled = false; schedule.nextRunAt = null; schedule.recoveryRequired = true;
            schedule.status = "RECOVERY_REQUIRED";
        }
        else
        {
            schedule.activeRunId = null;
            schedule.status = schedule.enabled ? "READY" : "PAUSED";
        }
        save(schedule);
    }
    private void skip(Schedule schedule, Instant now, String reason)
    {
        schedule.skippedCount++; schedule.lastSkippedAt = now.toString(); schedule.lastSkippedReason = reason; save(schedule);
    }
    private void save(Schedule schedule) { schedule.updatedAt = clock.instant().toString(); store.saveSchedule(schedule); }
    private Release ownedRelease(String id, long owner)
    {
        Release release = store.release(id);
        if (release == null || release.ownerId != owner) throw new ServiceException("已发布版本不存在或无权访问");
        return release;
    }
    private Schedule ownedSchedule(String id, long owner)
    {
        Schedule schedule = store.schedule(id);
        if (schedule == null || schedule.ownerId != owner) throw new ServiceException("定时任务不存在或无权访问");
        return schedule;
    }
    private static void revision(Schedule schedule, Long revision)
    {
        if (revision == null || revision != schedule.revision) throw new ServiceException("任务配置已变更，请刷新后重试", 409);
    }
    private static String name(String value)
    {
        if (value == null || value.isBlank() || value.trim().length() > 80 || value.chars().anyMatch(Character::isISOControl))
            throw new ServiceException("名称需要 1 至 80 个字符且不能包含控制字符");
        return value.trim();
    }
    static String next(String expression, String zone, Instant after)
    {
        if (expression == null || expression.isBlank() || expression.length() > 120) throw new ServiceException("请输入有效的 Quartz Cron（6 或 7 个字段）");
        if (zone == null || !ZoneId.getAvailableZoneIds().contains(zone)) throw new ServiceException("请选择有效的 IANA 时区，例如 Asia/Shanghai");
        try
        {
            CronExpression cron = new CronExpression(expression.trim()); cron.setTimeZone(TimeZone.getTimeZone(ZoneId.of(zone)));
            Date next = cron.getNextValidTimeAfter(Date.from(after)); return next == null ? null : next.toInstant().toString();
        }
        catch (Exception e) { throw new ServiceException("Cron 表达式无效，需要 Quartz 6 或 7 字段语法"); }
    }
    private ReleaseSummary summary(Release r)
    { return new ReleaseSummary(r.id, r.version, r.snapshot.run.flowId, r.snapshot.run.projectId, r.name, r.snapshot.run.definitionHash, r.createdAt, MODE); }
    private ScheduleSummary summary(Schedule s)
    {
        return new ScheduleSummary(s.id, s.revision, s.name, s.releaseId, s.flowId, s.cron, s.timeZone, s.enabled, s.nextRunAt,
            s.status, s.activeRunId, s.lastRunId, s.lastRunStatus, s.lastRunAt, s.lastFinishedAt, s.lastError, s.skippedCount,
            s.lastSkippedAt, s.lastSkippedReason, s.recoveryRequired, s.createdAt, s.updatedAt, MODE);
    }
    @PreDestroy public synchronized void close()
    {
        closed = true; if (timer != null) timer.shutdownNow();
        // The dependent execution service cancels its active work on close. Persist a recovery barrier here,
        // so even a shutdown timeout cannot silently permit the same schedule to overlap after restart.
        if (initialized)
            for (Schedule schedule : store.schedules())
                if (schedule.activeRunId != null)
                {
                    schedule.enabled = false; schedule.nextRunAt = null; schedule.recoveryRequired = true;
                    schedule.status = "RECOVERY_REQUIRED"; schedule.lastError = "服务已停止，请恢复并确认上次执行已清理"; save(schedule);
                }
    }
}
