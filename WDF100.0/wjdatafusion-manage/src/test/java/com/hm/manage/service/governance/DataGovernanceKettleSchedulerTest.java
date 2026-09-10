package com.hm.manage.service.governance;

import com.fasterxml.jackson.databind.*;
import com.hm.common.exception.ServiceException;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static com.hm.manage.service.governance.DataGovernanceKettleScheduler.*;
import static com.hm.manage.service.governance.DataGovernanceKettleService.*;

class DataGovernanceKettleSchedulerTest
{
    @TempDir Path temporary;
    final ObjectMapper mapper = new ObjectMapper();
    MutableClock clock = new MutableClock();
    DataGovernanceKettleProperties properties;
    DataGovernanceKettleService service;
    DataGovernanceKettleScheduler scheduler;
    RecordingWorker worker;
    @BeforeEach void open() throws Exception
    {
        properties = new DataGovernanceKettleProperties(); properties.setEnabled(true); properties.setStorageDir(temporary.toString());
        worker = new RecordingWorker(properties); service = new DataGovernanceKettleService(properties, DataGovernanceKettleApiTest.crypto(), worker);
        scheduler = new DataGovernanceKettleScheduler(service, properties, clock, Runnable::run);
    }
    @AfterEach void close() { scheduler.close(); service.close(); }
    String definition()
    {
        String xml = "<transformation><info><name>native-source</name></info><step><name>query</name><type>TableInput</type><sql>SELECT old_value FROM synthetic_source</sql><password>synthetic-cron-secret</password></step></transformation>";
        return (String)service.save(null, new DefinitionInput("原生查询", null, DataGovernanceKettleXml.encode(xml)), 7).get("id");
    }
    Input input(Long revision, long definitionRevision) { return new Input("原生计划", revision, definitionRevision, "*/5 * * * * ?", "Asia/Shanghai"); }
    @Test void frozenXmlAndAssetsExecuteWithoutReadingCurrentDefinitionAndIntentPrecedesWorker() throws Exception
    {
        String id = definition(); var asset = service.upload(id, "input.csv", bytes("original-file"), 7);
        View plan = scheduler.create(id, input(null, 1), 7); assertFalse(plan.enabled()); assertNull(plan.nextRunAt());
        String returned = DataGovernanceKettleXml.decode((String)service.definition(id, 7).get("xmlBase64"));
        service.save(id, new DefinitionInput("新画布", 1L, DataGovernanceKettleXml.encode(returned.replace("old_value", "new_value"))), 7);
        service.deleteFile(id, asset.id(), 7); service.upload(id, "input.csv", bytes("changed-file"), 7);
        worker.beforeSubmit = () -> {
            View pending = scheduler.list(id, 7).get(0); assertNotNull(pending.activeRunId());
            assertTrue(Files.isRegularFile(temporary.resolve("runs").resolve(pending.activeRunId() + ".json")));
        };
        View run = scheduler.runNow(plan.id(), 7);
        assertTrue(worker.prepared.path("xml").asText().contains("SELECT old_value"));
        assertFalse(worker.prepared.path("xml").asText().contains("new_value"));
        assertEquals("original-file", new String(Base64.getDecoder().decode(worker.lastSubmit.path("inputFiles").get(0).path("contentBase64").asText()), StandardCharsets.UTF_8));
        assertEquals(1, run.definitionRevision()); assertEquals(1, worker.submissions);
        try (var paths = Files.list(temporary.resolve("schedule-snapshots")))
        { String disk = Files.readString(paths.findFirst().orElseThrow()); assertFalse(disk.contains("old_value")); assertFalse(disk.contains("input.csv")); assertFalse(disk.contains("synthetic-cron-secret")); }
        assertThrows(ServiceException.class, () -> scheduler.update(plan.id(), input(1L, 2), 7));
    }
    @Test void cronDoesNotOverlapAndLaterTicksUseNativeXmlAgain()
    {
        String id = definition(); View plan = scheduler.create(id, input(null, 1), 7);
        plan = scheduler.state(plan.id(), new StateInput(true, 1L), 7); assertEquals("2030-01-01T00:00:05Z", plan.nextRunAt());
        clock.advance(5); scheduler.tick(); assertEquals(1, worker.submissions);
        String planId = plan.id(); assertThrows(ServiceException.class, () -> scheduler.runNow(planId, 7));
        worker.readState = "RUNNING"; clock.advance(10); scheduler.tick(); assertEquals(1, worker.submissions);
        worker.readState = "SUCCEEDED"; clock.advance(1); scheduler.tick();
        View completed = scheduler.list(id, 7).get(0); assertNull(completed.activeRunId()); assertEquals("SUCCEEDED", completed.lastRunState());
        assertEquals("2030-01-01T00:00:20Z", completed.nextRunAt());
        clock.advance(4); scheduler.tick(); assertEquals(2, worker.submissions); assertTrue(worker.prepared.path("xml").asText().contains("SELECT old_value"));
        assertFalse(worker.lastSubmit.has("inputJson"));
    }
    @Test void missedCyclesAndPreRestartDueTimesAreNeverBackfilled()
    {
        String id = definition(); View plan = scheduler.create(id, input(null, 1), 7); scheduler.state(plan.id(), new StateInput(true, 1L), 7);
        clock.advance(31); scheduler.tick(); assertEquals(0, worker.submissions); assertEquals("2030-01-01T00:00:35Z", scheduler.list(id, 7).get(0).nextRunAt());
        scheduler.close(); clock.advance(100); scheduler = new DataGovernanceKettleScheduler(service, properties, clock, Runnable::run);
        assertEquals("2030-01-01T00:02:15Z", scheduler.list(id, 7).get(0).nextRunAt());
        scheduler.tick(); assertEquals(0, worker.submissions);
    }
    @Test void restartRequiresReadOnlyRecoveryAndDoesNotEnableOrResubmit()
    {
        String id = definition(); View plan = scheduler.create(id, input(null, 1), 7); View active = scheduler.runNow(plan.id(), 7);
        String runId = active.activeRunId(); scheduler.close();
        scheduler = new DataGovernanceKettleScheduler(service, properties, clock, Runnable::run);
        View restarted = scheduler.list(id, 7).get(0); assertFalse(restarted.enabled()); assertTrue(restarted.recoveryRequired()); assertEquals(runId, restarted.activeRunId());
        clock.advance(100); scheduler.tick(); assertEquals(1, worker.submissions);
        worker.readState = "RUNNING"; View recovered = scheduler.recover(plan.id(), 7);
        assertEquals("RUNNING", recovered.status()); assertFalse(recovered.enabled()); assertFalse(recovered.recoveryRequired());
        worker.readState = "SUCCEEDED"; scheduler.tick(); assertNull(scheduler.list(id, 7).get(0).activeRunId());
        assertEquals("PAUSED", scheduler.list(id, 7).get(0).status()); assertEquals(1, worker.submissions);
    }
    @Test void unknownSubmissionPausesAndRecoveryOnlyQueriesExistingRun()
    {
        String id = definition(); View plan = scheduler.create(id, input(null, 1), 7); worker.failSubmit = true;
        View unknown = scheduler.runNow(plan.id(), 7); assertEquals("SUBMISSION_UNKNOWN", unknown.status()); assertTrue(unknown.recoveryRequired()); assertFalse(unknown.enabled());
        assertThrows(ServiceException.class, () -> scheduler.runNow(plan.id(), 7));
        assertThrows(ServiceException.class, () -> scheduler.state(plan.id(), new StateInput(true, 1L), 7));
        clock.advance(100); scheduler.tick(); scheduler.recover(plan.id(), 7); assertEquals(1, worker.submissions);
        assertTrue(scheduler.list(id, 7).get(0).recoveryRequired());
        worker.readState = "SUCCEEDED"; View resolved = scheduler.recover(plan.id(), 7);
        assertNull(resolved.activeRunId()); assertFalse(resolved.enabled()); assertEquals(1, worker.submissions);
    }
    @Test void queuedIntentWithoutJournalRemainsBlockedAfterRestart()
    {
        scheduler.close(); List<Runnable> queue = new ArrayList<>(); scheduler = new DataGovernanceKettleScheduler(service, properties, clock, queue::add);
        String id = definition(); View plan = scheduler.create(id, input(null, 1), 7); View intent = scheduler.runNow(plan.id(), 7);
        assertNotNull(intent.activeRunId()); assertTrue(service.runs(id, 7).isEmpty()); scheduler.close();
        scheduler = new DataGovernanceKettleScheduler(service, properties, clock, Runnable::run);
        View unresolved = scheduler.recover(plan.id(), 7); assertTrue(unresolved.recoveryRequired()); assertEquals(intent.activeRunId(), unresolved.activeRunId());
        assertThrows(ServiceException.class, () -> scheduler.runNow(plan.id(), 7)); queue.get(0).run(); assertEquals(0, worker.submissions); assertTrue(worker.calls.isEmpty());
    }
    @Test void knownValidationFailureAndStopPauseWithoutAutomaticRetry()
    {
        String id = definition(); View plan = scheduler.create(id, input(null, 1), 7); worker.invalidSave = true;
        View failed = scheduler.runNow(plan.id(), 7); assertEquals("VALIDATION_FAILED", failed.status()); assertNull(failed.activeRunId());
        clock.advance(100); scheduler.tick(); assertEquals(0, worker.submissions);
        worker.invalidSave = false; scheduler.runNow(plan.id(), 7); worker.readState = "STOPPED"; scheduler.tick();
        assertEquals("STOPPED", scheduler.list(id, 7).get(0).lastRunState()); assertFalse(scheduler.list(id, 7).get(0).enabled());
        clock.advance(100); scheduler.tick(); assertEquals(1, worker.submissions);
    }
    @Test void nativeTerminalEventWithoutProcessExitRetainsOverlapBarrier()
    {
        String id = definition(); View plan = scheduler.create(id, input(null, 1), 7); scheduler.runNow(plan.id(), 7);
        worker.readState = "SUCCEEDED"; worker.processFinished = false; scheduler.tick();
        assertEquals("FINISHING", scheduler.list(id, 7).get(0).status());
        assertThrows(ServiceException.class, () -> scheduler.runNow(plan.id(), 7));
        worker.readState = null; scheduler.tick(); assertTrue(scheduler.list(id, 7).get(0).recoveryRequired());
        worker.processFinished = true; worker.readState = "SUCCEEDED"; scheduler.recover(plan.id(), 7); assertNull(scheduler.list(id, 7).get(0).activeRunId());
        assertEquals(1, worker.submissions);
    }
    @Test void ownerRevisionAndIanaZoneChecksOccurBeforeAnyWorkerCall()
    {
        String id = definition(); View plan = scheduler.create(id, input(null, 1), 7);
        assertThrows(ServiceException.class, () -> scheduler.create(id, input(null, 2), 7));
        assertThrows(ServiceException.class, () -> scheduler.list(id, 8));
        assertThrows(ServiceException.class, () -> scheduler.update(plan.id(), input(1L, 1), 8));
        assertThrows(ServiceException.class, () -> scheduler.state(plan.id(), new StateInput(true, 1L), 8));
        assertThrows(ServiceException.class, () -> scheduler.runNow(plan.id(), 8));
        assertThrows(ServiceException.class, () -> scheduler.recover(plan.id(), 8));
        assertThrows(ServiceException.class, () -> scheduler.state(plan.id(), new StateInput(true, 0L), 7));
        assertThrows(ServiceException.class, () -> scheduler.create(id, new Input("bad", null, 1L, "invalid", "Asia/Shanghai"), 7));
        assertThrows(ServiceException.class, () -> scheduler.create(id, new Input("bad", null, 1L, "*/5 * * * * ?", "GMT+08:00"), 7));
        assertTrue(worker.calls.isEmpty());
    }
    @Test void metadataListDoesNotReadEncryptedSnapshotAndCoordinatorLockPreventsDuplicates() throws Exception
    {
        String id = definition(); View plan = scheduler.create(id, input(null, 1), 7);
        try (var files = Files.list(temporary.resolve("schedule-snapshots"))) { Files.writeString(files.findFirst().orElseThrow(), "unreadable synthetic snapshot"); }
        assertEquals(plan.id(), scheduler.list(id, 7).get(0).id());
        DataGovernanceKettleScheduler second = new DataGovernanceKettleScheduler(service, properties, clock, Runnable::run);
        try { assertThrows(ServiceException.class, () -> second.list(id, 7)); } finally { second.close(); }
        assertTrue(worker.calls.isEmpty());
    }
    private static ByteArrayInputStream bytes(String value) { return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8)); }
    static class MutableClock extends Clock
    {
        Instant now = Instant.parse("2030-01-01T00:00:00Z"); void advance(long seconds) { now = now.plusSeconds(seconds); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
    static class RecordingWorker extends DataGovernanceKettleApiTest.FakeWorker
    {
        JsonNode prepared; Runnable beforeSubmit; boolean processFinished = true;
        RecordingWorker(DataGovernanceKettleProperties p) { super(p); }
        @Override public JsonNode request(String method, String path, Object body)
        {
            if (method.equals("PUT")) prepared = mapper.valueToTree(body);
            if (method.equals("POST") && path.equals("/runs") && beforeSubmit != null) beforeSubmit.run();
            JsonNode response = super.request(method, path, body);
            if (processFinished && Set.of("SUCCEEDED", "FAILED", "STOPPED", "TIMED_OUT").contains(response.path("state").asText()))
                ((com.fasterxml.jackson.databind.node.ObjectNode)response).put("exitCode", 0);
            return response;
        }
    }
}
