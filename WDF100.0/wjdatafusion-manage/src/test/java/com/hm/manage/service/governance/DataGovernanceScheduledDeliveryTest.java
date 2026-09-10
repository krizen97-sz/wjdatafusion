package com.hm.manage.service.governance;

import com.hm.common.exception.ServiceException;
import com.hm.manage.service.governance.DataGovernanceModels.*;
import com.hm.manage.service.governance.DataGovernanceScheduleModels.*;
import com.hm.manage.service.governance.DataGovernanceScheduleDelivery.*;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DataGovernanceScheduledDeliveryTest
{
    @TempDir Path directory;
    DataGovernanceService service;
    DataGovernanceNifiClient client;
    DataGovernanceScheduleStore store;
    DataGovernanceScheduleDelivery bridge;
    DataGovernanceScheduler scheduler;
    DataGovernanceSchedulerTest.MutableClock clock;
    ReleaseSummary release;
    TestRun run;
    Delivery delivery;
    final String flow = UUID.randomUUID().toString(), connection = UUID.randomUUID().toString(), job = UUID.randomUUID().toString();
    final Binding original = new Binding(connection, "合成交付目标", "a".repeat(64));
    @BeforeEach void setup()
    {
        var properties = new DataGovernanceProperties(); properties.setStorageDir(directory.toString());
        service = mock(DataGovernanceService.class); client = mock(DataGovernanceNifiClient.class); bridge = mock(DataGovernanceScheduleDelivery.class);
        when(client.configured()).thenReturn(true); store = new DataGovernanceScheduleStore(properties);
        clock = new DataGovernanceSchedulerTest.MutableClock("2026-09-10T00:00:00Z"); scheduler = newScheduler();
        when(service.prepareSnapshot(eq(flow), any(), eq(7L))).thenAnswer(call -> {
            StoredRun snapshot = new StoredRun(); snapshot.ownerId = 7; snapshot.inputJson = "{}"; snapshot.run = new TestRun();
            snapshot.run.flowId = flow; snapshot.run.projectId = flow; snapshot.run.definitionHash = "frozen"; return snapshot;
        });
        when(service.submitFrozen(any(), eq(7L), anyString())).thenAnswer(call -> {
            run = new TestRun(); run.id = call.getArgument(2); run.status = "RUNNING"; run.updatedAt = clock.instant().toString(); return run;
        });
        when(service.run(anyString(), eq(7L))).thenAnswer(call -> run);
        when(bridge.target(connection, 7)).thenReturn(original);
        delivery = new Delivery(job, "QUEUED", null, original.fingerprint());
        when(bridge.submit(anyString(), any(), eq(7L))).thenAnswer(call -> delivery);
        when(bridge.status(job, 7)).thenAnswer(call -> delivery);
        when(bridge.find(anyString(), eq(connection), eq(7L))).thenAnswer(call -> delivery);
        release = scheduler.publish(new PublishRequest(flow, "固定批次", "{}", Map.of()), 7);
    }
    DataGovernanceScheduler newScheduler() { return new DataGovernanceScheduler(service, store, client, clock, () -> bridge); }
    @AfterEach void close() { scheduler.close(); store.close(); }
    ScheduleSummary create() { return scheduler.create(new ScheduleRequest("执行后交付", release.id(), "0 * * * * ?", "Asia/Shanghai", null, connection), 7); }
    ScheduleSummary current() { return scheduler.schedules(7, null).get(0); }
    void successfulRun() { run.status = "SUCCEEDED"; run.cleanupConfirmed = true; run.artifactsManifestAvailable = true; run.artifactCount = 2; }

    @Test void successfulEngineRunRetainsOccupancyUntilDeliveryConfirmedAndNeverResubmitsCompletedJob()
    {
        var created = create(); var plan = scheduler.state(created.id(), new StateRequest(true, created.revision()), 7);
        scheduler.runNow(plan.id(), 7); successfulRun(); scheduler.tick();
        assertEquals("DELIVERING", current().status()); assertEquals("QUEUED", current().deliveryStatus()); assertNotNull(current().activeRunId());
        assertThrows(ServiceException.class, () -> scheduler.runNow(plan.id(), 7));
        clock.set("2026-09-10T00:01:00Z"); scheduler.tick(); assertEquals(2, current().skippedCount());
        delivery = new Delivery(job, "RUNNING", null, original.fingerprint()); scheduler.tick(); assertNotNull(current().activeRunId());
        run.artifactsManifestAvailable = false; // Once submitted, status is authoritative; do not attempt manifest-based re-submission.
        delivery = new Delivery(job, "DELIVERED", null, original.fingerprint()); scheduler.tick();
        assertNull(current().activeRunId()); assertEquals("DELIVERED", current().deliveryStatus()); assertNotNull(current().deliveryFinishedAt());
        scheduler.tick(); scheduler.close(); scheduler = newScheduler(); scheduler.tick();
        verify(service, times(1)).submitFrozen(any(), eq(7L), anyString()); verify(bridge, times(1)).submit(anyString(), eq(original), eq(7L));
    }
    @Test void completeManifestAndCleanupAreBothRequiredBeforeAnyTransfer()
    {
        var plan = create(); scheduler.runNow(plan.id(), 7); run.status = "SUCCEEDED"; run.cleanupConfirmed = true; scheduler.tick();
        assertTrue(current().recoveryRequired()); assertEquals("MANIFEST_REQUIRED", current().deliveryStatus()); assertNotNull(current().activeRunId());
        verify(bridge, never()).submit(anyString(), any(), anyLong());
    }
    @Test void cleanupFailureNeverTransfersAndEmptyOrFailedRunsClearlySkipDelivery()
    {
        var plan = create(); scheduler.runNow(plan.id(), 7); successfulRun(); run.cleanupConfirmed = false; scheduler.tick();
        assertTrue(current().recoveryRequired()); verify(bridge, never()).submit(anyString(), any(), anyLong());
    }
    @Test void emptyAndFailedRunsReleaseWithoutDelivery()
    {
        var plan = create(); scheduler.runNow(plan.id(), 7); run.status = "EMPTY"; run.cleanupConfirmed = true; scheduler.tick();
        assertNull(current().activeRunId()); assertEquals("SKIPPED_EMPTY", current().deliveryStatus());
        scheduler.runNow(plan.id(), 7); run.status = "FAILED"; run.cleanupConfirmed = true; run.error = "合成执行失败"; scheduler.tick();
        assertNull(current().activeRunId()); assertEquals("NOT_REQUESTED", current().deliveryStatus()); assertEquals("FAILED", current().lastRunStatus());
        verify(bridge, never()).submit(anyString(), any(), anyLong());
    }
    @Test void targetChangeBeforeRunPausesWithoutStartingEngineAndExplicitEditRebinds()
    {
        var plan = create(); when(bridge.target(connection, 7)).thenReturn(new Binding(connection, "目标改名", "b".repeat(64)));
        assertThrows(ServiceException.class, () -> scheduler.runNow(plan.id(), 7));
        assertTrue(current().recoveryRequired()); assertEquals("TARGET_CHANGED", current().deliveryStatus());
        verify(service, never()).submitFrozen(any(), anyLong(), anyString());
        var recovered = scheduler.recover(plan.id(), 7);
        var edited = scheduler.update(plan.id(), new ScheduleRequest("重新绑定", release.id(), plan.cron(), plan.timeZone(), recovered.revision(), connection), 7);
        assertEquals("b".repeat(64), edited.deliveryTargetFingerprint()); scheduler.runNow(plan.id(), 7); assertNotNull(current().activeRunId());
    }
    @Test void targetChangeAfterEngineCompletionStillPreventsTransfer()
    {
        var plan = create(); scheduler.runNow(plan.id(), 7); successfulRun();
        when(bridge.target(connection, 7)).thenReturn(new Binding(connection, "新目标", "b".repeat(64))); scheduler.tick();
        assertEquals("TARGET_CHANGED", current().deliveryStatus()); assertTrue(current().recoveryRequired()); verify(bridge, never()).submit(anyString(), any(), anyLong());
    }
    @Test void deliveryIntentSurvivesUncertainSubmissionAndRestartRecoveryNeverRetries()
    {
        var plan = create(); scheduler.runNow(plan.id(), 7); successfulRun();
        when(bridge.submit(anyString(), any(), eq(7L))).thenAnswer(call -> {
            assertNotNull(store.schedule(plan.id()).deliveryIntentAt); assertEquals("SUBMITTING", store.schedule(plan.id()).deliveryStatus);
            throw new ServiceException("合成不确定提交");
        });
        scheduler.tick(); assertEquals("UNKNOWN", current().deliveryStatus()); assertTrue(current().recoveryRequired());
        scheduler.close(); scheduler = newScheduler(); scheduler.tick(); when(bridge.find(run.id, connection, 7)).thenReturn(null);
        assertThrows(ServiceException.class, () -> scheduler.recover(plan.id(), 7)); assertNotNull(current().activeRunId());
        when(bridge.find(run.id, connection, 7)).thenReturn(new Delivery(job, "DELIVERED", null, original.fingerprint()));
        var recovered = scheduler.recover(plan.id(), 7); assertFalse(recovered.recoveryRequired()); assertFalse(recovered.enabled()); assertNull(recovered.activeRunId());
        verify(service, times(1)).submitFrozen(any(), anyLong(), anyString()); verify(bridge, times(1)).submit(anyString(), any(), anyLong());
    }
    @Test void restartBeforeDeliveryIntentOnlyChecksAnExistingJob()
    {
        var plan = create(); scheduler.runNow(plan.id(), 7); scheduler.close(); successfulRun(); scheduler = newScheduler(); scheduler.tick();
        when(bridge.find(run.id, connection, 7)).thenReturn(null);
        assertThrows(ServiceException.class, () -> scheduler.recover(plan.id(), 7)); verify(bridge, never()).submit(anyString(), any(), anyLong());
        when(bridge.find(run.id, connection, 7)).thenReturn(new Delivery(job, "DELIVERED", null, original.fingerprint()));
        assertFalse(scheduler.recover(plan.id(), 7).recoveryRequired());
    }
    @Test void failedTransferRequiresExternalRetryAndRecoveryDoesNotRecomputeTheRun()
    {
        var plan = create(); scheduler.runNow(plan.id(), 7); successfulRun(); scheduler.tick();
        delivery = new Delivery(job, "FAILED", "合成传输失败", original.fingerprint()); scheduler.tick(); assertTrue(current().recoveryRequired());
        delivery = new Delivery(job, "RUNNING", null, original.fingerprint()); assertThrows(ServiceException.class, () -> scheduler.recover(plan.id(), 7));
        assertEquals("RECOVERY_REQUIRED", current().status());
        delivery = new Delivery(job, "DELIVERED", null, original.fingerprint()); assertFalse(scheduler.recover(plan.id(), 7).recoveryRequired());
        verify(service, times(1)).submitFrozen(any(), anyLong(), anyString()); verify(bridge, times(1)).submit(anyString(), any(), anyLong());
    }
    @Test void ownerMismatchOrMissingOptionalBridgeCannotBindDelivery()
    {
        assertThrows(ServiceException.class, () -> scheduler.create(new ScheduleRequest("越权", release.id(), "0 * * * * ?", "UTC", null, connection), 8));
        verify(bridge, never()).target(anyString(), eq(8L));
        scheduler.close(); scheduler = new DataGovernanceScheduler(service, store, client, clock);
        assertThrows(ServiceException.class, this::create);
        var old = scheduler.create(new ScheduleRequest("无交付旧计划", release.id(), "0 * * * * ?", "UTC", null), 7);
        scheduler.runNow(old.id(), 7); successfulRun(); scheduler.tick(); assertNull(current().activeRunId());
    }
    @Test void completedJobWithDifferentFrozenTargetCannotReleaseOrRecoverTheSchedule()
    {
        var plan = create(); scheduler.runNow(plan.id(), 7); successfulRun(); scheduler.tick();
        delivery = new Delivery(job, "DELIVERED", null, "b".repeat(64)); scheduler.tick();
        assertTrue(current().recoveryRequired()); assertEquals("TARGET_MISMATCH", current().deliveryStatus()); assertNotNull(current().activeRunId());
        assertThrows(ServiceException.class, () -> scheduler.recover(plan.id(), 7)); assertNotNull(current().activeRunId());
        delivery = new Delivery(job, "DELIVERED", null, original.fingerprint());
        assertNull(scheduler.recover(plan.id(), 7).activeRunId()); verify(bridge, times(1)).submit(anyString(), any(), anyLong());
    }
    @Test void missingFingerprintIsUnconfirmedAndNameOnlyChangesDoNotChangeTargetIdentity()
    {
        var plan = create(); when(bridge.target(connection, 7)).thenReturn(new Binding(connection, "修改显示名", original.fingerprint()));
        scheduler.runNow(plan.id(), 7); successfulRun(); delivery = new Delivery(job, "DELIVERED", null); scheduler.tick();
        assertTrue(current().recoveryRequired()); assertEquals("TARGET_MISMATCH", current().deliveryStatus()); assertNotNull(current().activeRunId());
    }

    @Test void malformedCompletedReceiptCannotBeAcceptedDuringRecovery()
    {
        var plan = create(); scheduler.runNow(plan.id(), 7); successfulRun(); scheduler.close(); scheduler = newScheduler(); scheduler.tick();
        when(bridge.find(run.id, connection, 7)).thenReturn(new Delivery(null, "DELIVERED", null, original.fingerprint()));
        assertThrows(ServiceException.class, () -> scheduler.recover(plan.id(), 7));
        assertTrue(current().recoveryRequired()); assertNotNull(current().activeRunId()); verify(bridge, never()).submit(anyString(), any(), anyLong());
    }
    @Test void issuedDeliveryStillOwnsTheSlotIfSourceMetadataLaterChanges()
    {
        var plan = create(); scheduler.runNow(plan.id(), 7); successfulRun(); scheduler.tick();
        run.status = "FAILED"; scheduler.tick(); assertNotNull(current().activeRunId());
        scheduler.close(); scheduler = newScheduler(); scheduler.tick();
        assertThrows(ServiceException.class, () -> scheduler.recover(plan.id(), 7)); assertNotNull(current().activeRunId());
        verify(bridge, times(1)).submit(anyString(), any(), anyLong());
    }

}
