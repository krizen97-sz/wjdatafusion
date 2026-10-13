package com.hm.manage.service.governance.hdi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hm.common.constant.Constants;
import com.hm.common.core.domain.entity.SysUser;
import com.hm.framework.web.service.SysPermissionService;
import com.hm.system.service.ISysUserService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.quartz.CronExpression;
import org.quartz.CalendarIntervalScheduleBuilder;
import org.quartz.CalendarIntervalTrigger;
import org.quartz.DateBuilder;
import org.quartz.TriggerBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Persistent triggering of frozen platform revisions from Cron expressions produced by the original SDK. */
@Service
public class HdiCoreSchedules {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private final HdiCoreProperties properties;
    private final HdiTaskStore store;
    private final HdiCompilerClient compiler;
    private final HdiCoreRuns runs;
    private final ISysUserService users;
    private final SysPermissionService permissions;
    private final Clock clock;
    private final Instant startedAt;
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(task -> { Thread thread = new Thread(task, "hdi-original-schedules"); thread.setDaemon(true); return thread; });
    private volatile boolean closed;

    @Autowired public HdiCoreSchedules(HdiCoreProperties properties, HdiTaskStore store, HdiCompilerClient compiler,
                                      HdiCoreRuns runs, ISysUserService users, SysPermissionService permissions) {
        this(properties, store, compiler, runs, users, permissions, Clock.systemUTC());
    }
    HdiCoreSchedules(HdiCoreProperties properties, HdiTaskStore store, HdiCompilerClient compiler,
                     HdiCoreRuns runs, ISysUserService users, SysPermissionService permissions, Clock clock) {
        this.properties = properties; this.store = store; this.compiler = compiler; this.runs = runs;
        this.users = users; this.permissions = permissions; this.clock = clock; startedAt = clock.instant();
    }
    @PostConstruct void initialize() {
        if (properties.isEnabled()) timer.scheduleWithFixedDelay(this::tickSafely, 1, 1, java.util.concurrent.TimeUnit.SECONDS);
    }
    public Map<String,Integer> supportedFrequencies() {
        Map<String,Integer> values = new LinkedHashMap<>(); values.put("一次性", 0); values.put("每月", 2); values.put("每周", 3); values.put("每日", 4); values.put("小时", 5); values.put("分钟", 6); return values;
    }
    public List<String> preview(JsonNode originalSchedule) {
        enabled(); JsonNode compiled = compiler.schedule(originalSchedule, ZONE.getId());
        if ("MANUAL".equals(compiled.path("mode").asText())) return List.of();
        ObjectNode plan = planFromCompiled(compiled, 1);
        List<String> result = new ArrayList<>(); Instant cursor = clock.instant();
        for (int i = 0; i < 5; i++) {
            cursor = nextPlan(plan, cursor);
            result.add(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZONE).format(cursor));
        }
        return result;
    }
    public JsonNode get(long owner, long taskId) {
        HdiTaskStore.SchedulePlan plan = store.getSchedule(owner, taskId);
        if (plan == null) return HdiProtocol.JSON.createObjectNode().put("configured", false).put("enabled", false).put("timezone", ZONE.getId());
        return view(plan);
    }
    public JsonNode save(long owner, long taskId, long expectedRevision, JsonNode originalSchedule, boolean activate) {
        enabled(); store.get(owner, taskId);
        JsonNode compiled = compiler.schedule(originalSchedule, ZONE.getId());
        if (!Set.of("CRON", "CALENDAR").contains(compiled.path("mode").asText())) throw new HdiTaskFault(422, "手动启动设置不能作为周期计划启用");
        ObjectNode plan = planFromCompiled(compiled, expectedRevision);
        Instant fire = activate ? nextPlan(plan, clock.instant()) : null;
        return view(store.saveSchedule(owner, taskId, expectedRevision, plan.toString(), activate, fire));
    }
    private ObjectNode planFromCompiled(JsonNode compiled, long revision) {
        String kind = compiled.path("mode").asText();
        ObjectNode plan = HdiProtocol.JSON.createObjectNode().put("protocolVersion", 1).put("kind", kind).put("revision", Long.toString(revision)).put("zoneId", ZONE.getId());
        plan.set("scheduleConfig", compiled.path("scheduleConfig")); plan.set("cronExpressions", compiled.path("cronExpressions"));
        if (kind.equals("CALENDAR")) {
            plan.set("calendar", compiled.path("calendar")); plan.set("nativeExpression", compiled.path("nativeExpression"));
            // First original calendar trigger is immediate; the platform's durable poll starts it on the next second.
            // This persisted anchor is retained for later fires, instead of aligning to wall-clock Cron boundaries.
            plan.put("anchorTime", clock.instant().plusSeconds(1).toString());
        }
        return plan;
    }
    public JsonNode saveManual(long owner, long taskId, long expectedRevision, JsonNode originalSchedule) {
        enabled(); store.get(owner, taskId);
        JsonNode compiled = compiler.schedule(originalSchedule, ZONE.getId());
        if (!"MANUAL".equals(compiled.path("mode").asText())) throw new HdiTaskFault(422, "当前设置不是原手动启动方式");
        ObjectNode plan = HdiProtocol.JSON.createObjectNode().put("protocolVersion", 1).put("kind", "MANUAL").put("revision", Long.toString(expectedRevision)).put("zoneId", ZONE.getId());
        plan.set("scheduleConfig", compiled.path("scheduleConfig")); plan.set("cronExpressions", HdiProtocol.JSON.createArrayNode());
        // The store disables the previous periodic generation in this same transaction. Running is a separate explicit action.
        return view(store.saveSchedule(owner, taskId, expectedRevision, plan.toString(), false, null));
    }
    public void disable(long owner, long taskId) { store.disableSchedule(owner, taskId); }
    private JsonNode view(HdiTaskStore.SchedulePlan plan) {
        JsonNode parsed = plan(plan);
        ObjectNode result = HdiProtocol.JSON.createObjectNode().put("configured", true).put("enabled", plan.enabled())
            .put("revision", Long.toString(plan.revision())).put("generation", Long.toString(plan.generation()))
            .put("kind", parsed.path("kind").asText("CRON"))
            .put("timezone", parsed.path("zoneId").asText()).put("lastOutcome", plan.lastOutcome());
        result.set("schedule", parsed.path("scheduleConfig"));
        if (plan.nextFireAt() == null) result.putNull("nextFireAt"); else result.put("nextFireAt", plan.nextFireAt().toString());
        return result;
    }
    void tick() {
        if (!properties.isEnabled() || closed) return;
        Instant now = clock.instant();
        for (HdiTaskStore.SchedulePlan plan : store.dueSchedules(now, 100)) {
            if (closed) return;
            JsonNode config = plan(plan); Instant expected = plan.nextFireAt();
            if (expected == null) continue;
            Instant next = nextPlan(config, now);
            if (expected.isBefore(startedAt)) {
                store.skipScheduledFire(plan, expected, next, "SKIPPED_RESTART"); continue;
            }
            if (Duration.between(expected, now).compareTo(Duration.ofSeconds(30)) > 0) {
                store.skipScheduledFire(plan, expected, next, "SKIPPED_MISFIRE"); continue;
            }
            if (!mayRun(plan.ownerId())) {
                store.skipScheduledFire(plan, expected, next, "SKIPPED_PERMISSION"); continue;
            }
            String trigger = "schedule:" + plan.taskId() + ":" + plan.generation() + ":" + expected.toEpochMilli();
            // The store atomically advances this generation and reserves the run (including overlap protection).
            HdiTaskStore.Run run = store.claimScheduledRun(plan, expected, next, trigger);
            if (run != null) runs.dispatchReservedSchedule(run);
        }
    }
    private boolean mayRun(long owner) {
        SysUser user = users.selectUserById(owner);
        if (user == null || !"0".equals(user.getStatus()) || !"0".equals(user.getDelFlag())) return false;
        Set<String> grants = permissions.getMenuPermission(user);
        return grants != null && (grants.contains(Constants.ALL_PERMISSION) || grants.containsAll(Set.of("governance:flow:list", "governance:flow:test")));
    }
    private static JsonNode plan(HdiTaskStore.SchedulePlan plan) {
        try {
            JsonNode value = HdiProtocol.JSON.readTree(plan.planJson());
            if (!value.isObject() || value.path("protocolVersion").asInt(-1) != 1 || !Long.toString(plan.revision()).equals(value.path("revision").asText())
                || !value.path("scheduleConfig").isObject() || !value.path("cronExpressions").isArray()) throw new IllegalArgumentException();
            String kind = value.path("kind").asText("CRON");
            if (kind.equals("MANUAL")) {
                if (plan.enabled() || plan.nextFireAt() != null || !value.path("cronExpressions").isEmpty()) throw new IllegalArgumentException();
            } else if (kind.equals("CALENDAR")) {
                if (!value.path("cronExpressions").isEmpty()) throw new IllegalArgumentException();
                calendar(value, ZoneId.of(value.path("zoneId").asText()));
            } else if (!kind.equals("CRON") || value.path("cronExpressions").isEmpty()) throw new IllegalArgumentException();
            ZoneId.of(value.path("zoneId").asText()); return value;
        } catch (Exception error) { throw new HdiTaskFault(502, "持久化原调度计划无效，未执行该计划"); }
    }
    static Instant nextPlan(JsonNode plan, Instant after) {
        ZoneId zone = ZoneId.of(plan.path("zoneId").asText());
        if (!"CALENDAR".equals(plan.path("kind").asText("CRON"))) return next(plan.path("cronExpressions"), zone, after);
        Date value = calendar(plan, zone).getFireTimeAfter(Date.from(after));
        if (value == null || !value.toInstant().isAfter(after)) throw new HdiTaskFault(422, "原间隔调度没有后续执行时间");
        return value.toInstant();
    }
    private static CalendarIntervalTrigger calendar(JsonNode plan, ZoneId zone) {
        try {
            JsonNode definition = plan.path("calendar");
            if (!definition.path("interval").isInt() || definition.path("interval").intValue() < 1
                || !Set.of("HOUR", "MINUTE").contains(definition.path("unit").asText())) throw new IllegalArgumentException();
            DateBuilder.IntervalUnit unit = DateBuilder.IntervalUnit.valueOf(definition.path("unit").asText());
            int count = definition.path("interval").intValue();
            if (!(count + "#" + (unit == DateBuilder.IntervalUnit.HOUR ? "5" : "6")).equals(plan.path("nativeExpression").asText())) throw new IllegalArgumentException();
            return TriggerBuilder.newTrigger().startAt(Date.from(Instant.parse(plan.path("anchorTime").asText())))
                .withSchedule(CalendarIntervalScheduleBuilder.calendarIntervalSchedule().withInterval(count, unit).inTimeZone(TimeZone.getTimeZone(zone))).build();
        } catch (Exception failure) { throw new HdiTaskFault(422, "原间隔调度定义无效"); }
    }
    static Instant next(JsonNode expressions, ZoneId zone, Instant after) {
        if (!expressions.isArray() || expressions.isEmpty() || expressions.size() > 32) throw new HdiTaskFault(422, "原调度未生成有效周期");
        Instant best = null;
        try {
            for (JsonNode expression : expressions) {
                if (!expression.isTextual() || expression.asText().length() > 256) throw new IllegalArgumentException();
                CronExpression cron = new CronExpression(expression.asText()); cron.setTimeZone(TimeZone.getTimeZone(zone));
                Date value = cron.getNextValidTimeAfter(Date.from(after));
                if (value != null && (best == null || value.toInstant().isBefore(best))) best = value.toInstant();
            }
        } catch (Exception error) { throw new HdiTaskFault(422, "原调度表达式无效"); }
        if (best == null || !best.isAfter(after)) throw new HdiTaskFault(422, "原调度没有后续执行时间");
        return best;
    }
    private void tickSafely() {
        try { tick(); }
        catch (RuntimeException failure) { /* Preserve durable due intent; never infer successful submission after a local failure. */ }
    }
    private void enabled() { if (!properties.isEnabled() || closed) throw new HdiTaskFault(503, "原周期调度尚未启用"); }
    @PreDestroy public void close() { closed = true; timer.shutdownNow(); }
}
