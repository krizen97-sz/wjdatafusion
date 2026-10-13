package com.hm.manage.service.governance;

import com.hm.common.exception.ServiceException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.quartz.CronExpression;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** In-process scheduling of frozen graphs. The store holds the cross-process exclusive lock. */
@Service
public class DataGovernanceLocalScheduler implements AutoCloseable
{
    public static class Schedule
    {
        public String id, name, definitionId, cron, timeZone, state, nextRunAt;
        public String activeRequestId, activeRunId, lastRequestId, lastRunId, lastRunState, lastError, createdAt, updatedAt;
        public long owner, revision, definitionRevision;
        public boolean enabled, recoveryRequired;
        public Map<String,Object> graph;
    }
    private static final String KIND = "schedules";
    private static final Set<String> TERMINAL = Set.of("SUCCEEDED", "FAILED", "STOPPED", "CANCELLED", "INTERRUPTED", "TIMED_OUT");
    private final DataGovernanceLocalStore store;
    private final DataGovernanceLocalService service;
    private final Clock clock;
    private final Executor executor;
    private final Set<String> inFlight = new HashSet<>();
    private ScheduledExecutorService timer;
    private boolean initialized, closed;
    private String coordinatorError;

    @Autowired public DataGovernanceLocalScheduler(DataGovernanceLocalStore store, DataGovernanceLocalService service)
    { this(store, service, Clock.systemUTC(), Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "local-flow-schedule-operation"); t.setDaemon(true); return t; })); }
    DataGovernanceLocalScheduler(DataGovernanceLocalStore store, DataGovernanceLocalService service, Clock clock, Executor executor)
    { this.store = store; this.service = service; this.clock = clock; this.executor = executor; }

    @PostConstruct public synchronized void start()
    {
        if (closed || timer != null) return;
        try { initialize(); }
        catch (ServiceException error) { coordinatorError = "计划存储不可用，自动触发已暂停"; return; }
        timer = Executors.newSingleThreadScheduledExecutor(r -> { Thread t = new Thread(r, "local-flow-schedule-tick"); t.setDaemon(true); return t; });
        timer.scheduleWithFixedDelay(() -> {
            try { tick(); }
            catch (Exception error) { synchronized (this) { coordinatorError = "计划协调器已暂停，请检查本机存储"; } }
        }, 1, 1, TimeUnit.SECONDS);
    }
    private synchronized void initialize()
    {
        if (closed) reject("计划协调器已关闭");
        if (coordinatorError != null) reject(coordinatorError);
        if (initialized) return;
        for (Schedule schedule : stored())
        {
            if (pending(schedule)) recovery(schedule, "服务已重启，请核查原运行；不会自动重发");
            else if (schedule.enabled) advance(schedule, clock.instant());
            persist(schedule);
        }
        initialized = true;
    }
    public synchronized List<Map<String,Object>> list(String definitionId, long owner)
    {
        initialize();
        return stored().stream().filter(s -> s.owner == owner && Objects.equals(s.definitionId, definitionId))
            .sorted(Comparator.comparing((Schedule s) -> s.createdAt).reversed()).map(this::view).toList();
    }
    public synchronized Map<String,Object> save(String scheduleId, String definitionId, Map<String,Object> body, long owner)
    {
        initialize();
        if (body == null) reject("请提供计划配置");
        boolean creating = scheduleId == null;
        Schedule schedule = creating ? new Schedule() : owned(scheduleId, owner);
        if (!creating) { revision(schedule, number(body.get("revision"))); idle(schedule); if (!schedule.definitionId.equals(definitionId)) reject("计划所属流程不可修改"); }
        if (creating && stored().stream().filter(s -> s.owner == owner).count() >= 100) reject("最多可保存 100 个计划");
        String name = text(body.get("name"));
        if (name.isBlank() || name.length() > 80 || name.codePoints().anyMatch(Character::isISOControl)) reject("计划名称须为 1 至 80 个有效字符");
        String cron = text(body.get("cron")), zone = text(body.get("timeZone"));
        if (next(cron, zone, clock.instant()) == null) reject("该 Cron 没有后续执行时间");
        long definitionRevision = number(body.get("definitionRevision"));
        // capture validates the graph before any schedule mutation or execution intent is written.
        DataGovernanceLocalService.Definition definition = service.capture(definitionId, owner);
        if (definition.owner != owner || !Objects.equals(definition.id, definitionId)) reject("流程不存在或无权访问");
        if (definition.revision != definitionRevision) throw new ServiceException("流程修订已变化，请刷新后重新保存计划", 409);
        if (definition.graph == null || definition.graph.isEmpty()) reject("流程图不能为空");
        if (creating) { schedule.id = UUID.randomUUID().toString(); schedule.owner = owner; schedule.definitionId = definitionId; schedule.createdAt = clock.instant().toString(); }
        schedule.name = name; schedule.cron = cron; schedule.timeZone = zone; schedule.graph = definition.graph;
        schedule.definitionRevision = definitionRevision; schedule.revision++;
        schedule.enabled = false; schedule.recoveryRequired = false; schedule.nextRunAt = null; schedule.state = "PAUSED"; schedule.lastError = null;
        persist(schedule); return view(schedule);
    }
    public synchronized Map<String,Object> enable(String id, boolean enabled, long revision, long owner)
    {
        initialize(); Schedule schedule = owned(id, owner); revision(schedule, revision);
        if (enabled) { idle(schedule); advance(schedule, clock.instant()); if (!schedule.enabled) reject("该 Cron 没有后续执行时间"); schedule.lastError = null; }
        else { schedule.enabled = false; schedule.nextRunAt = null; if (!pending(schedule)) schedule.state = "PAUSED"; }
        schedule.revision++; persist(schedule); return view(schedule);
    }
    public synchronized Map<String,Object> runNow(String id, long owner)
    {
        initialize(); Schedule schedule = owned(id, owner); idle(schedule);
        if (globallyBusy()) reject("已有计划正在提交、运行或等待核查，请完成后再运行");
        trigger(schedule); return view(owned(id, owner));
    }
    public Map<String,Object> recover(String id, long owner)
    {
        Schedule schedule;
        synchronized (this)
        {
            initialize(); schedule = owned(id, owner);
            if (inFlight.contains(id)) reject("该计划正在提交或核查，请稍后刷新");
            if (!pending(schedule)) return view(schedule);
            schedule.enabled = false; schedule.nextRunAt = null; persist(schedule); inFlight.add(id);
        }
        try
        {
            // A missing request is still an uncertain intent; recovery must never submit it again.
            Map<String,Object> result = schedule.activeRunId == null
                ? service.findRequest(schedule.definitionId, schedule.activeRequestId, owner) : service.run(schedule.activeRunId, owner);
            synchronized (this) { if (closed) return view(schedule); Schedule current = owned(id, owner); if (sameIntent(schedule, current)) observe(current, result); return view(current); }
        }
        catch (Exception error)
        { synchronized (this) { if (closed) return view(schedule); Schedule current = owned(id, owner); recovery(current, "未取得原运行记录，保留提交意图；不会自动重发"); persist(current); return view(current); } }
        finally { synchronized (this) { inFlight.remove(id); } }
    }
    synchronized void tick()
    {
        if (closed || coordinatorError != null) return;
        initialize(); Instant now = clock.instant();
        // Poll first, including paused schedules whose already accepted run is still active.
        for (Schedule schedule : stored())
            if (schedule.activeRunId != null && !schedule.recoveryRequired && !inFlight.contains(schedule.id)) poll(schedule);
        for (Schedule schedule : stored())
        {
            if (!schedule.enabled || schedule.nextRunAt == null || Instant.parse(schedule.nextRunAt).isAfter(now)) continue;
            if (pending(schedule) || inFlight.contains(schedule.id) || globallyBusy() || Duration.between(Instant.parse(schedule.nextRunAt), now).toMillis() >= 1000)
            { advanceDueTime(schedule, now); persist(schedule); continue; }
            trigger(schedule);
        }
    }
    private void trigger(Schedule schedule)
    {
        idle(schedule);
        schedule.activeRequestId = UUID.randomUUID().toString(); schedule.lastRequestId = schedule.activeRequestId;
        schedule.activeRunId = null; schedule.state = "SUBMITTING"; schedule.lastRunState = "SUBMITTING"; schedule.lastError = null;
        if (schedule.enabled) advanceDueTime(schedule, clock.instant());
        persist(schedule); // Durable request identity precedes submitFrozen, including queued dispatch.
        inFlight.add(schedule.id);
        dispatch(schedule, () -> service.submitFrozen(schedule.definitionId, schedule.owner, schedule.definitionRevision, schedule.graph, schedule.activeRequestId));
    }
    private void poll(Schedule schedule)
    {
        inFlight.add(schedule.id); dispatch(schedule, () -> service.run(schedule.activeRunId, schedule.owner));
    }
    private void dispatch(Schedule intent, java.util.function.Supplier<Map<String,Object>> action)
    {
        try
        {
            executor.execute(() -> {
                try
                {
                    synchronized (this) { if (closed) return; }
                    Map<String,Object> result = action.get();
                    synchronized (this) { if (!closed) { Schedule current = owned(intent.id, intent.owner); if (sameIntent(intent, current)) observe(current, result); } }
                }
                catch (Exception error)
                { synchronized (this) { if (!closed) { Schedule current = owned(intent.id, intent.owner); recovery(current, "提交或核查结果尚未确认，已暂停计划；请核查原请求"); persist(current); } } }
                finally { synchronized (this) { inFlight.remove(intent.id); } }
            });
        }
        catch (RuntimeException error)
        { inFlight.remove(intent.id); Schedule current = owned(intent.id, intent.owner); recovery(current, "执行队列未确认接收，已保留提交意图"); persist(current); }
    }
    private void observe(Schedule schedule, Map<String,Object> result)
    {
        if (result == null || !(result.get("id") instanceof String) || ((String)result.get("id")).isBlank())
        { recovery(schedule, "未找到原请求对应的运行，保留核查屏障；不会自动重发"); persist(schedule); return; }
        String id = (String)result.get("id");
        if (schedule.activeRunId != null && !schedule.activeRunId.equals(id)) reject("返回的运行与计划不匹配");
        schedule.activeRunId = id; schedule.lastRunId = id;
        String state = text(result.get("state")); schedule.lastRunState = state;
        if (TERMINAL.contains(state) && Boolean.TRUE.equals(result.get("finalized")))
        {
            schedule.activeRunId = null; schedule.activeRequestId = null; schedule.recoveryRequired = false;
            if (!"SUCCEEDED".equals(state)) { schedule.enabled = false; schedule.nextRunAt = null; schedule.state = state; schedule.lastError = "上次运行未成功，计划已暂停；不会自动重试"; }
            else { schedule.lastError = null; if (schedule.enabled) advance(schedule, clock.instant()); else schedule.state = "PAUSED"; }
        }
        else if (TERMINAL.contains(state)) { schedule.state = "FINISHING"; schedule.recoveryRequired = false; }
        else if (Set.of("QUEUED", "RUNNING", "STOPPING", "CANCELLING").contains(state)) { schedule.state = state; schedule.recoveryRequired = false; }
        else recovery(schedule, "运行状态尚未确认，请核查原运行；不会重复提交");
        persist(schedule);
    }
    private static boolean sameIntent(Schedule first, Schedule second) { return Objects.equals(first.activeRequestId, second.activeRequestId); }
    private static boolean pending(Schedule schedule) { return schedule.activeRequestId != null || schedule.activeRunId != null || schedule.recoveryRequired; }
    private boolean globallyBusy() { return !inFlight.isEmpty() || stored().stream().anyMatch(DataGovernanceLocalScheduler::pending); }
    private void idle(Schedule schedule) { if (pending(schedule) || inFlight.contains(schedule.id)) reject("该计划仍有未决运行或提交意图，禁止重叠执行或修改冻结版本"); }
    private void recovery(Schedule schedule, String error) { schedule.enabled = false; schedule.nextRunAt = null; schedule.recoveryRequired = true; schedule.state = "RECOVERY_REQUIRED"; schedule.lastError = error; }
    private void advance(Schedule schedule, Instant now) { schedule.enabled = true; advanceDueTime(schedule, now); schedule.state = schedule.enabled ? "READY" : "EXHAUSTED"; }
    private void advanceDueTime(Schedule schedule, Instant now) { schedule.nextRunAt = next(schedule.cron, schedule.timeZone, now); if (schedule.nextRunAt == null) { schedule.enabled = false; if (!pending(schedule)) schedule.state = "EXHAUSTED"; } }
    private List<Schedule> stored() { return store.list(KIND, Schedule.class); }
    private Schedule owned(String id, long owner) { Schedule schedule = store.read(KIND, id, Schedule.class); if (schedule.owner != owner) throw new ServiceException("计划不存在或无权访问", 404); return schedule; }
    private void persist(Schedule schedule) { if (closed) reject("计划协调器已关闭"); schedule.updatedAt = clock.instant().toString(); store.write(KIND, schedule.id, schedule); }
    private static void revision(Schedule schedule, long value) { if (value != schedule.revision) throw new ServiceException("计划修订已变化，请刷新", 409); }
    private static long number(Object value) { if (!(value instanceof Number number) || number.doubleValue() != number.longValue() || number.longValue() < 1) { reject("请提供有效修订版本"); return 0; } return number.longValue(); }
    private static String text(Object value) { return value instanceof String ? ((String)value).trim() : ""; }
    static String next(String cron, String zone, Instant after)
    {
        if (cron == null || cron.isBlank() || cron.length() > 120 || zone == null || !ZoneId.getAvailableZoneIds().contains(zone)) { reject("请提供有效 Quartz Cron 和 IANA 时区"); return null; }
        try { CronExpression expression = new CronExpression(cron.trim()); expression.setTimeZone(TimeZone.getTimeZone(ZoneId.of(zone))); Date value = expression.getNextValidTimeAfter(Date.from(after)); return value == null ? null : value.toInstant().toString(); }
        catch (Exception error) { throw new ServiceException("Quartz Cron 表达式无效"); }
    }
    private Map<String,Object> view(Schedule schedule)
    {
        Map<String,Object> value = new LinkedHashMap<>();
        value.put("id", schedule.id); value.put("name", schedule.name); value.put("revision", schedule.revision);
        value.put("definitionId", schedule.definitionId); value.put("definitionRevision", schedule.definitionRevision);
        value.put("cron", schedule.cron); value.put("timeZone", schedule.timeZone); value.put("enabled", schedule.enabled); value.put("state", schedule.state);
        value.put("activeRequestId", schedule.activeRequestId); value.put("activeRunId", schedule.activeRunId); value.put("lastRequestId", schedule.lastRequestId);
        value.put("lastRunId", schedule.lastRunId); value.put("lastRunState", schedule.lastRunState); value.put("nextRunAt", schedule.nextRunAt);
        value.put("lastError", schedule.lastError); value.put("recoveryRequired", schedule.recoveryRequired); value.put("createdAt", schedule.createdAt); value.put("updatedAt", schedule.updatedAt);
        return value;
    }
    private static void reject(String message) { throw new ServiceException(message); }
    @Override @PreDestroy public synchronized void close()
    { closed = true; if (timer != null) timer.shutdownNow(); if (executor instanceof ExecutorService pool) pool.shutdownNow(); }
}
