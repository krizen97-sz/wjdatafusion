package com.hm.manage.service.governance.hdi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hm.common.core.domain.entity.SysUser;
import com.hm.framework.web.service.SysPermissionService;
import com.hm.system.service.ISysUserService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HdiCoreSchedulesTest {
    HdiCoreProperties properties = new HdiCoreProperties();
    HdiTaskStore store = mock(HdiTaskStore.class);
    HdiCompilerClient compiler = mock(HdiCompilerClient.class);
    HdiCoreRuns runs = mock(HdiCoreRuns.class);
    ISysUserService users = mock(ISysUserService.class);
    SysPermissionService permissions = mock(SysPermissionService.class);
    MutableClock clock = new MutableClock(Instant.parse("2026-10-13T10:59:58Z"));
    HdiCoreSchedules schedules;
    ObjectNode config = HdiProtocol.JSON.createObjectNode().put("scheduleType", 2).put("unit", 4).put("timeInterval", "1").put("executeTime", "19:00:00").put("flowIncrStatus", 1);

    @BeforeEach void setup() {
        properties.setEnabled(true);
        SysUser user = new SysUser(); user.setUserId(4L); user.setStatus("0"); user.setDelFlag("0");
        when(users.selectUserById(4L)).thenReturn(user);
        when(permissions.getMenuPermission(user)).thenReturn(Set.of("governance:flow:list", "governance:flow:test"));
        ObjectNode compiled = HdiProtocol.JSON.createObjectNode(); compiled.set("scheduleConfig", config); compiled.set("cronExpressions", HdiProtocol.JSON.createArrayNode().add("0 0 19 * * ?"));
        when(compiler.schedule(any(), eq("Asia/Shanghai"))).thenReturn(compiled);
        schedules = new HdiCoreSchedules(properties, store, compiler, runs, users, permissions, clock);
    }
    @AfterEach void cleanup() { schedules.close(); }
    HdiTaskStore.SchedulePlan plan(Instant fire) {
        ObjectNode value = HdiProtocol.JSON.createObjectNode().put("protocolVersion", 1).put("revision", "2").put("zoneId", "Asia/Shanghai");
        value.set("scheduleConfig", config); value.set("cronExpressions", HdiProtocol.JSON.createArrayNode().add("0 0 19 * * ?"));
        return new HdiTaskStore.SchedulePlan(17, 4, 2, 3, value.toString(), fire, true, "ENABLED");
    }
    @Test void onlyAdvertisesSupportedOriginalCycles() {
        assertEquals(Map.of("每月", 2, "每周", 3, "每日", 4), schedules.supportedFrequencies());
    }
    @Test void previewUsesOriginalCompiledCronWithExplicitPlatformTimezone() {
        List<String> times = schedules.preview(config);
        assertEquals(List.of("2026-10-13 19:00:00", "2026-10-14 19:00:00", "2026-10-15 19:00:00", "2026-10-16 19:00:00", "2026-10-17 19:00:00"), times);
        verify(compiler).schedule(config, "Asia/Shanghai");
    }
    @Test void savesOriginalConfigurationAndFrozenRevisionAsPersistentPlan() {
        when(store.saveSchedule(eq(4L), eq(17L), eq(2L), anyString(), eq(true), any())).thenAnswer(call -> {
            JsonNode json = HdiProtocol.JSON.readTree((String)call.getArgument(3));
            assertEquals("2", json.path("revision").asText()); assertEquals(config, json.path("scheduleConfig"));
            assertEquals(Instant.parse("2026-10-13T11:00:00Z"), call.getArgument(5));
            return new HdiTaskStore.SchedulePlan(17, 4, 2, 1, json.toString(), call.getArgument(5), true, "ENABLED");
        });
        JsonNode saved = schedules.save(4, 17, 2, config, true);
        assertTrue(saved.path("enabled").asBoolean()); assertEquals("Asia/Shanghai", saved.path("timezone").asText());
        verifyNoInteractions(runs);
    }
    @Test void dueTriggerUsesAtomicClaimAndNeverReservesASecondRunItself() {
        var plan = plan(Instant.parse("2026-10-13T11:00:00Z")); clock.now = Instant.parse("2026-10-13T11:00:01Z");
        when(store.dueSchedules(any(), eq(100))).thenReturn(List.of(plan));
        HdiTaskStore.Run run = HdiWorkerClientTest.run();
        when(store.claimScheduledRun(eq(plan), eq(plan.nextFireAt()), eq(Instant.parse("2026-10-14T11:00:00Z")), anyString())).thenReturn(run, null);
        schedules.tick(); schedules.tick();
        verify(runs, times(1)).dispatchReservedSchedule(run);
        verify(store, never()).reserveRun(anyLong(), anyLong(), anyLong(), anyString(), anyString());
    }
    @Test void startupDoesNotReplayOverduePlans() {
        var plan = plan(Instant.parse("2026-10-12T11:00:00Z")); when(store.dueSchedules(any(), eq(100))).thenReturn(List.of(plan));
        schedules.tick(); verify(store).skipScheduledFire(eq(plan), eq(plan.nextFireAt()), eq(Instant.parse("2026-10-13T11:00:00Z")), eq("SKIPPED_RESTART"));
        verifyNoInteractions(runs);
    }
    @Test void latePollSkipsMisfireRatherThanBurstReplaying() {
        var plan = plan(Instant.parse("2026-10-13T11:00:00Z")); clock.now = Instant.parse("2026-10-13T11:00:31Z");
        when(store.dueSchedules(any(), eq(100))).thenReturn(List.of(plan)); schedules.tick();
        verify(store).skipScheduledFire(eq(plan), eq(plan.nextFireAt()), any(), eq("SKIPPED_MISFIRE")); verifyNoInteractions(runs);
    }
    @Test void revokedPermissionsSkipTriggerUsingLiveDatabaseUser() {
        var plan = plan(Instant.parse("2026-10-13T11:00:00Z")); clock.now = Instant.parse("2026-10-13T11:00:01Z");
        when(store.dueSchedules(any(), eq(100))).thenReturn(List.of(plan)); when(permissions.getMenuPermission(any())).thenReturn(Set.of("governance:flow:list"));
        schedules.tick(); verify(store).skipScheduledFire(eq(plan), eq(plan.nextFireAt()), any(), eq("SKIPPED_PERMISSION")); verifyNoInteractions(runs);
    }
    @Test void accountLookupFailureDoesNotPretendPermissionWasRevoked() {
        var plan = plan(Instant.parse("2026-10-13T11:00:00Z")); clock.now = Instant.parse("2026-10-13T11:00:01Z");
        when(store.dueSchedules(any(), eq(100))).thenReturn(List.of(plan)); when(users.selectUserById(4L)).thenThrow(new IllegalStateException("database unavailable"));
        assertThrows(IllegalStateException.class, schedules::tick);
        verify(store, never()).skipScheduledFire(any(), any(), any(), anyString()); verifyNoInteractions(runs);
    }
    @Test void staleOrOverlappingClaimDoesNotDispatch() {
        var plan = plan(Instant.parse("2026-10-13T11:00:00Z")); clock.now = Instant.parse("2026-10-13T11:00:01Z");
        when(store.dueSchedules(any(), eq(100))).thenReturn(List.of(plan)); when(store.claimScheduledRun(any(), any(), any(), anyString())).thenReturn(null);
        schedules.tick(); verifyNoInteractions(runs);
    }
    @Test void disablesFutureTriggersWithoutInventingAStoppedCurrentRun() {
        schedules.disable(4, 17); verify(store).disableSchedule(4, 17); verifyNoInteractions(runs);
    }
    @Test void malformedPersistedPlanIsNotExecuted() {
        var plan = new HdiTaskStore.SchedulePlan(17, 4, 2, 3, "{}", clock.instant(), true, "ENABLED");
        when(store.dueSchedules(any(), eq(100))).thenReturn(List.of(plan));
        assertEquals(502, assertThrows(HdiTaskFault.class, schedules::tick).status()); verifyNoInteractions(runs);
    }
    static class MutableClock extends Clock {
        Instant now; MutableClock(Instant now) { this.now = now; }
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
    }
}
