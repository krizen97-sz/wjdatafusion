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
        Map<String,Integer> values = new LinkedHashMap<>(); values.put("每月", 2); values.put("每周", 3); values.put("每日", 4); return values;
    }
    public List<String> preview(JsonNode originalSchedule) {
        enabled(); JsonNode compiled = compiler.schedule(originalSchedule, ZONE.getId());
        List<String> result = new ArrayList<>(); Instant cursor = clock.instant();
        for (int i = 0; i < 5; i++) {
            cursor = next(compiled.path("cronExpressions"), ZONE, cursor);
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
        ObjectNode plan = HdiProtocol.JSON.createObjectNode().put("protocolVersion", 1).put("revision", Long.toString(expectedRevision)).put("zoneId", ZONE.getId());
        plan.set("scheduleConfig", compiled.path("scheduleConfig")); plan.set("cronExpressions", compiled.path("cronExpressions"));
        Instant fire = activate ? next(plan.path("cronExpressions"), ZONE, clock.instant()) : null;
        return view(store.saveSchedule(owner, taskId, expectedRevision, plan.toString(), activate, fire));
    }
    public void disable(long owner, long taskId) { store.disableSchedule(owner, taskId); }
    private JsonNode view(HdiTaskStore.SchedulePlan plan) {
        JsonNode parsed = plan(plan);
        ObjectNode result = HdiProtocol.JSON.createObjectNode().put("configured", true).put("enabled", plan.enabled())
            .put("revision", Long.toString(plan.revision())).put("generation", Long.toString(plan.generation()))
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
            ZoneId zone = ZoneId.of(config.path("zoneId").asText());
            Instant next = next(config.path("cronExpressions"), zone, now);
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
                || !value.path("scheduleConfig").isObject() || !value.path("cronExpressions").isArray() || value.path("cronExpressions").isEmpty()) throw new IllegalArgumentException();
            ZoneId.of(value.path("zoneId").asText()); return value;
        } catch (Exception error) { throw new HdiTaskFault(502, "持久化原调度计划无效，未执行该计划"); }
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
