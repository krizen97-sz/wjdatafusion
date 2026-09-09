package com.hm.manage.service.governance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hm.common.exception.ServiceException;
import com.hm.manage.service.governance.DataGovernanceModels.*;
import com.hm.manage.service.governance.DataGovernanceScheduleModels.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DataGovernanceSchedulerTest
{
    @TempDir Path directory;
    private DataGovernanceService service;
    private DataGovernanceNifiClient client;
    private DataGovernanceScheduleStore store;
    private DataGovernanceScheduler scheduler;
    private MutableClock clock;
    private ReleaseSummary release;
    private TestRun observed;
    private final String flowId = "00000000-0000-0000-0000-000000000004";
    @BeforeEach void setup()
    {
        DataGovernanceProperties properties = new DataGovernanceProperties(); properties.setStorageDir(directory.toString());
        service = mock(DataGovernanceService.class); client = mock(DataGovernanceNifiClient.class); when(client.configured()).thenReturn(true);
        store = new DataGovernanceScheduleStore(properties); clock = new MutableClock("2026-09-09T00:00:00Z");
        scheduler = new DataGovernanceScheduler(service, store, client, clock);
        when(service.prepareSnapshot(eq(flowId), any(), eq(7L))).thenAnswer(call -> {
            StoredRun snapshot = new StoredRun(); snapshot.ownerId = 7;
            TestInput input = call.getArgument(1); snapshot.inputJson = input.inputJson(); snapshot.parameters = input.parameters();
            snapshot.run = new TestRun(); snapshot.run.flowId = flowId; snapshot.run.projectId = flowId;
            snapshot.run.definitionHash = "frozen-hash"; snapshot.run.definitionCapturedAt = clock.instant().toString();
            snapshot.definition = new ObjectMapper().valueToTree(Map.of("synthetic", "private-definition")); return snapshot;
        });
        when(service.submitFrozen(any(), eq(7L), anyString())).thenAnswer(call -> {
            observed = new TestRun(); observed.id = call.getArgument(2); observed.status = "RUNNING";
            observed.flowId = flowId; observed.updatedAt = clock.instant().toString(); return observed;
        });
        when(service.run(anyString(), eq(7L))).thenAnswer(call -> observed);
        release = scheduler.publish(new PublishRequest(flowId, "版本", "{\"secretSample\":1}", Map.of()), 7);
    }
    @AfterEach void close() { scheduler.close(); store.close(); }
    private ScheduleSummary create()
    { return scheduler.create(new ScheduleRequest("每天固定批次", release.id(), "0 * * * * ?", "Asia/Shanghai", null), 7); }
    private ScheduleSummary current()
    { return scheduler.schedules(7, null).get(0); }
    private ScheduleSummary enable(ScheduleSummary schedule)
    { return scheduler.state(schedule.id(), new StateRequest(true, schedule.revision()), 7); }

    @Test void publishedVersionsAreImmutablePrivateAndOwnerScoped() throws Exception
    {
        ReleaseSummary second = scheduler.publish(new PublishRequest(flowId, "版本二", "{}", Map.of()), 7);
        assertEquals(2, second.version()); assertEquals(1, release.version());
        String summaries = new ObjectMapper().writeValueAsString(scheduler.releases(7, flowId));
        assertFalse(summaries.contains("secretSample")); assertFalse(summaries.contains("private-definition"));
        assertEquals("FIXED_JSON_BATCH", release.inputMode());
        assertEquals("{\"secretSample\":1}", scheduler.release(release.id(), 7).inputJson());
        assertTrue(scheduler.releases(8, null).isEmpty());
        assertThrows(ServiceException.class, () -> scheduler.release(release.id(), 8));
        assertThrows(ServiceException.class, () -> store.createRelease(store.release(release.id())));
        assertEquals("rw-------", java.nio.file.attribute.PosixFilePermissions.toString(Files.getPosixFilePermissions(directory.resolve("schedules/release-" + release.id() + ".json"))));
    }
    @Test void defaultPauseEnableRevisionAndTimezoneAreExplicit()
    {
        ScheduleSummary schedule = create(); assertFalse(schedule.enabled()); assertNull(schedule.nextRunAt());
        schedule = enable(schedule); assertEquals("2026-09-09T00:01:00Z", schedule.nextRunAt());
        String id = schedule.id();
        assertThrows(ServiceException.class, () -> scheduler.state(id, new StateRequest(false, 1L), 7));
        assertThrows(ServiceException.class, () -> scheduler.runNow(id, 8));
        assertThrows(ServiceException.class, () -> scheduler.create(new ScheduleRequest("错误时区", release.id(), "0 0 * * * ?", "invented/zone", null), 7));
        assertThrows(ServiceException.class, () -> DataGovernanceScheduler.next("* * *", "Asia/Shanghai", clock.instant()));
        assertEquals("2026-09-09T01:00:00Z", DataGovernanceScheduler.next("0 0 9 * * ?", "Asia/Shanghai", clock.instant()));
        assertEquals("2026-09-09T13:00:00Z", DataGovernanceScheduler.next("0 0 9 * * ?", "America/New_York", clock.instant()));
    }
    @Test void waitsForRealTerminalAndCleanupAndSkipsOverlappingTriggers()
    {
        ScheduleSummary schedule = enable(create()); clock.set("2026-09-09T00:01:00Z"); scheduler.tick();
        assertNotNull(current().activeRunId()); assertEquals("RUNNING", current().lastRunStatus());
        assertNull(current().lastFinishedAt());
        assertThrows(ServiceException.class, () -> scheduler.runNow(schedule.id(), 7));
        clock.set("2026-09-09T00:02:00Z"); scheduler.tick(); assertEquals(2, current().skippedCount());
        verify(service, times(1)).submitFrozen(any(), eq(7L), anyString());
        observed.status = "SUCCEEDED"; observed.cleanupConfirmed = true; observed.updatedAt = clock.instant().toString();
        scheduler.tick(); assertNull(current().activeRunId()); assertEquals("SUCCEEDED", current().lastRunStatus());
        assertNotNull(current().lastFinishedAt()); assertEquals("READY", current().status());
        clock.set("2026-09-09T00:03:00Z"); scheduler.tick(); verify(service, times(2)).submitFrozen(any(), eq(7L), anyString());
    }
    @Test void failureIsRealAndCleanupBarrierRequiresExplicitRecovery()
    {
        ScheduleSummary schedule = create(); scheduler.runNow(schedule.id(), 7);
        observed.status = "CLEANUP_REQUIRED"; observed.cleanupConfirmed = false; observed.error = "synthetic cleanup failure";
        scheduler.tick(); assertTrue(current().recoveryRequired()); assertFalse(current().enabled());
        assertEquals("CLEANUP_REQUIRED", current().lastRunStatus());
        assertThrows(ServiceException.class, () -> scheduler.runNow(schedule.id(), 7));
        when(service.cancel(anyString(), eq(7L))).thenReturn(observed);
        assertThrows(ServiceException.class, () -> scheduler.recover(schedule.id(), 7));
        observed.status = "CANCELLED"; observed.cleanupConfirmed = true;
        ScheduleSummary recovered = scheduler.recover(schedule.id(), 7);
        assertFalse(recovered.recoveryRequired()); assertNull(recovered.activeRunId()); assertFalse(recovered.enabled());
        scheduler.runNow(schedule.id(), 7); observed.status = "FAILED"; observed.error = "synthetic execution failure"; observed.cleanupConfirmed = true;
        scheduler.tick(); assertEquals("FAILED", current().lastRunStatus()); assertEquals("synthetic execution failure", current().lastError());
    }
    @Test void restartDoesNotCatchUpAndInterruptionsCannotAutomaticallyResume()
    {
        ScheduleSummary schedule = enable(create());
        scheduler.close(); clock.set("2026-09-09T00:10:30Z"); scheduler = new DataGovernanceScheduler(service, store, client, clock);
        scheduler.tick(); assertEquals("2026-09-09T00:11:00Z", current().nextRunAt()); verify(service, never()).submitFrozen(any(), anyLong(), anyString());
        scheduler.runNow(schedule.id(), 7); String runId = current().activeRunId();
        scheduler.close(); scheduler = new DataGovernanceScheduler(service, store, client, clock);
        scheduler.tick(); assertTrue(current().recoveryRequired()); assertFalse(current().enabled()); assertEquals(runId, current().activeRunId());
        assertThrows(ServiceException.class, () -> scheduler.runNow(schedule.id(), 7));
        observed.status = "CANCELLED"; observed.cleanupConfirmed = true;
        assertFalse(scheduler.recover(schedule.id(), 7).recoveryRequired());
    }
    @Test void persistIntentBeforeSubmissionAndRecoverInterruptedPreSubmission()
    {
        ScheduleSummary schedule = create();
        when(service.submitFrozen(any(), eq(7L), anyString())).thenAnswer(call -> {
            String runId = call.getArgument(2); assertEquals(runId, store.schedule(schedule.id()).activeRunId);
            assertEquals("STARTING", store.schedule(schedule.id()).status); throw new ServiceException("synthetic failure");
        });
        assertThrows(ServiceException.class, () -> scheduler.runNow(schedule.id(), 7));
        assertTrue(current().recoveryRequired());
        when(service.run(anyString(), eq(7L))).thenThrow(new ServiceException("测试记录不存在或无权访问"));
        assertFalse(scheduler.recover(schedule.id(), 7).recoveryRequired()); assertEquals("INTERRUPTED", current().lastRunStatus());
    }
    @Test void editingAlwaysPausesAndRunningDefinitionCannotBeRebound()
    {
        ScheduleSummary schedule = enable(create());
        ScheduleSummary edited = scheduler.update(schedule.id(), new ScheduleRequest("新版计划", release.id(), "0 0 9 * * ?", "Asia/Shanghai", schedule.revision()), 7);
        assertFalse(edited.enabled()); assertNull(edited.nextRunAt());
        scheduler.runNow(edited.id(), 7);
        assertThrows(ServiceException.class, () -> scheduler.update(edited.id(), new ScheduleRequest("修改中", release.id(), "0 0 9 * * ?", "Asia/Shanghai", edited.revision()), 7));
    }
    @Test void unconfiguredEngineStartsNoTimerAndMakesNoExecutionCalls()
    {
        when(client.configured()).thenReturn(false); scheduler.start(); scheduler.tick();
        assertThrows(ServiceException.class, () -> scheduler.runNow(create().id(), 7));
        verify(service, never()).submitFrozen(any(), anyLong(), anyString());
        verify(client, never()).json(anyString(), anyString(), any());
    }
    @Test void storeRejectsSecondWriterTraversalAndSymlinks() throws Exception
    {
        DataGovernanceProperties properties = new DataGovernanceProperties(); properties.setStorageDir(directory.toString());
        DataGovernanceScheduleStore second = new DataGovernanceScheduleStore(properties);
        try { assertThrows(ServiceException.class, second::schedules); } finally { second.close(); }
        assertThrows(ServiceException.class, () -> store.release("../../private"));
        String id = UUID.randomUUID().toString(); Path other = directory.resolve("other.json"); Files.writeString(other, "{}");
        Files.createSymbolicLink(directory.resolve("schedules/release-" + id + ".json"), other);
        assertThrows(ServiceException.class, () -> store.release(id));
    }
    static class MutableClock extends Clock
    {
        private Instant instant;
        MutableClock(String instant) { set(instant); }
        void set(String value) { instant = Instant.parse(value); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
