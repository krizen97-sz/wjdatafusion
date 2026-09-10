package com.hm.manage.service.governance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hm.common.exception.ServiceException;
import com.hm.manage.service.governance.DataGovernanceKafkaModels.*;
import com.hm.manage.service.governance.DataGovernanceModels.*;
import com.hm.manage.service.governance.DataGovernanceScheduleModels.Release;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static com.hm.manage.service.governance.DataGovernanceEngine.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DataGovernanceKafkaExecutionTest
{
    @TempDir Path root;
    final ObjectMapper mapper = new ObjectMapper();
    class Fixture implements AutoCloseable
    {
        final DataGovernanceProperties p = new DataGovernanceProperties();
        final DataGovernanceKafkaProperties kp = new DataGovernanceKafkaProperties();
        final DataGovernanceKafkaStore store;
        final DataGovernanceFileRunRepository runs;
        final DataGovernanceScheduleStore releases;
        final DataGovernanceArtifactStore artifacts;
        final DataGovernanceService engine = mock(DataGovernanceService.class);
        final DataGovernanceKafkaService kafka;
        DataGovernanceKafkaExecution execution;
        final ReceiptSummary receipt;
        final Release release;
        Fixture(boolean failSubmit) throws Exception
        {
            p.setStorageDir(root.resolve("run-store").toString()); p.getNifi().setBaseUrl("https://localhost:9443/nifi-api");
            kp.setEnabled(true); kp.setStorageDir(root.resolve("source-store").toString());
            store = new DataGovernanceKafkaStore(kp); runs = new DataGovernanceFileRunRepository(p); releases = new DataGovernanceScheduleStore(p); artifacts = new DataGovernanceArtifactStore(p);
            DataGovernanceKafkaTransport transport = binding -> new DataGovernanceKafkaTransport.Session() {
                public List<Integer> partitions() { return List.of(0); }
                public void ensureExclusiveGroup() { }
                public Map<Integer, Long> committed() { Map<Integer, Long> result = new HashMap<>(); result.put(0, null); return result; }
                public Map<Integer, Long> beginningOffsets() { return Map.of(0, 0L); }
                public List<DataGovernanceKafkaTransport.Record> fetch(Map<Integer, Long> starts, int limit) { return List.of(new DataGovernanceKafkaTransport.Record(0, 0, "{\"live\":true}".getBytes())); }
                public void commit(Map<Integer, Long> offsets) { throw new AssertionError("Execution must never commit offsets"); }
                public void close() { }
            };
            kafka = new DataGovernanceKafkaService(kp, store, runs, () -> transport, () -> null, () -> artifacts);
            var profile = kafka.create(new ProfileRequest("fixture", List.of("127.0.0.1:19092"), "fixture-topic", "rynew-governance-7-fixture", null), 7);
            receipt = kafka.receive(profile.id(), 7);
            String source = UUID.randomUUID().toString(), capture = UUID.randomUUID().toString();
            var graph = mapper.valueToTree(map("processors", List.of(
                map("component", map("id", source, "type", STANDARD + "GenerateFlowFile", "name", "source", "bundle", map("group", "test", "artifact", "test", "version", "1"), "config", map("properties", map()))),
                map("component", map("id", capture, "type", UPDATE, "name", "capture", "bundle", map("group", "test", "artifact", "test", "version", "1"), "config", map("comments", CAPTURE, "properties", map())))),
                "connections", List.of(map("component", map("source", map("id", source, "type", "PROCESSOR"), "destination", map("id", capture, "type", "PROCESSOR"), "selectedRelationships", List.of("success"))))));
            release = new Release(); release.id = UUID.randomUUID().toString(); release.ownerId = 7; release.version = 1; release.name = "immutable release"; release.createdAt = java.time.Instant.now().toString();
            release.snapshot = new StoredRun(); release.snapshot.ownerId = 7; release.snapshot.inputJson = "{\"fixed\":true}"; release.snapshot.parameters = Map.of(); release.snapshot.run = new TestRun();
            release.snapshot.run.flowId = UUID.randomUUID().toString(); release.snapshot.run.projectId = UUID.randomUUID().toString();
            release.snapshot.definition = new DataGovernanceSafeFlow(graph).freeze(release.snapshot.inputJson, Map.of()); release.snapshot.run.definitionHash = DataGovernanceSafeFlow.hash(release.snapshot.definition); releases.createRelease(release);
            doAnswer(call -> {
                if (failSubmit) throw new ServiceException("uncertain submission");
                StoredRun snapshot = call.getArgument(0); String id = call.getArgument(2);
                StoredRun stored = mapper.convertValue(snapshot, StoredRun.class); stored.run.id = id; stored.run.status = "SUCCEEDED"; stored.run.cleanupConfirmed = true;
                var stage = artifacts.begin(stored); byte[] bytes = stored.inputJson.getBytes(java.nio.charset.StandardCharsets.UTF_8); artifacts.capture(stage, "result.json", "application/json", bytes.length, out -> out.write(bytes)); artifacts.publish(stage, stored); runs.save(stored); return stored.run;
            }).when(engine).submitFrozen(any(), eq(7L), anyString());
            execution = new DataGovernanceKafkaExecution(p, kp, kafka, releases, engine, runs, artifacts, () -> null);
        }
        DataGovernanceKafkaExecution.View await(String status) throws Exception {
            long end = System.nanoTime() + 5_000_000_000L;
            while (System.nanoTime() < end) { var v = execution.view(receipt.id(), 7); if (status.equals(v.status())) return v; Thread.sleep(20); }
            throw new AssertionError("Execution status not reached: " + status);
        }
        public void close() { execution.close(); store.close(); runs.close(); releases.close(); }
    }
    @Test void receiptSubstitutesOnlyInputAndPersistsBothDefinitionHashesWithoutCommitting() throws Exception
    {
        try (Fixture f = new Fixture(false)) {
            var result = f.execution.execute(f.receipt.id(), new DataGovernanceKafkaExecution.Request(f.release.id, null), 7);
            var done = f.await("READY_TO_ACK");
            assertNotEquals(done.releaseHash(), done.executionHash()); assertEquals(f.release.snapshot.run.definitionHash, done.releaseHash());
            assertEquals(f.kafka.read(f.receipt.id(), 7).inputJson(), f.runs.find(done.runId()).inputJson);
            assertEquals("{\"fixed\":true}", f.releases.release(f.release.id).snapshot.inputJson);
            assertEquals(done.runId(), f.kafka.read(f.receipt.id(), 7).receipt().runId()); assertTrue(f.kafka.read(f.receipt.id(), 7).receipt().leaseHeld());
            assertEquals(result.runId(), f.execution.execute(f.receipt.id(), new DataGovernanceKafkaExecution.Request(f.release.id, null), 7).runId());
            verify(f.engine, times(1)).submitFrozen(any(), eq(7L), anyString());
            assertThrows(ServiceException.class, () -> f.execution.view(f.receipt.id(), 8));
        }
    }
    @Test void uncertainSubmissionReservesSourceAndRecoveryNeverCreatesASecondRun() throws Exception
    {
        try (Fixture f = new Fixture(true)) {
            f.execution.execute(f.receipt.id(), new DataGovernanceKafkaExecution.Request(f.release.id, null), 7); var original = f.await("RECOVERY_REQUIRED");
            assertThrows(ServiceException.class, () -> f.kafka.release(f.receipt.id(), 7));
            f.execution.close(); f.execution = new DataGovernanceKafkaExecution(f.p, f.kp, f.kafka, f.releases, f.engine, f.runs, f.artifacts, () -> null);
            assertEquals("RECOVERY_REQUIRED", f.execution.recover(f.receipt.id(), 7).status());
            assertEquals(original.runId(), f.execution.view(f.receipt.id(), 7).runId());
            verify(f.engine, times(1)).submitFrozen(any(), eq(7L), anyString());
        }
    }
    @Test void reservedExecutionCannotAttachADifferentRunOrReleaseItsLease() throws Exception
    {
        try (Fixture f = new Fixture(true)) {
            String planned = UUID.randomUUID().toString(); f.kafka.reserveExecution(f.receipt.id(), planned, f.receipt.inputSha256(), null, 7);
            assertThrows(ServiceException.class, () -> f.kafka.release(f.receipt.id(), 7));
            assertThrows(ServiceException.class, () -> f.kafka.reserveExecution(f.receipt.id(), UUID.randomUUID().toString(), f.receipt.inputSha256(), null, 7));
            verifyNoInteractions(f.engine);
        }
    }
}
