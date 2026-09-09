package com.hm.manage.service.governance;

import com.hm.common.exception.ServiceException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hm.manage.service.governance.DataGovernanceModels.*;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DataGovernanceRepositoryTest
{
    @TempDir Path directory;
    @Test void atomicRecordsSurviveReopenAndSecondWriterIsRejected()
    {
        DataGovernanceProperties properties = new DataGovernanceProperties(); properties.setStorageDir(directory.toString());
        var first = new DataGovernanceFileRunRepository(properties); var second = new DataGovernanceFileRunRepository(properties);
        StoredRun record = new StoredRun(); record.ownerId = 12; record.inputJson = "{}"; record.run = new TestRun();
        record.run.id = UUID.randomUUID().toString(); record.run.status = "RUNNING"; record.run.createdAt = Instant.now().toString();
        try
        {
            first.save(record);
            assertThrows(ServiceException.class, () -> second.save(record));
            assertThrows(ServiceException.class, () -> first.find("../../outside"));
            first.close(); second.close();
            var reopened = new DataGovernanceFileRunRepository(properties);
            try { assertEquals(12, reopened.find(record.run.id).ownerId); assertEquals("RUNNING", reopened.list().get(0).run.status); }
            finally { reopened.close(); }
        }
        finally { first.close(); second.close(); }
    }
    @Test void summaryScansOmitPayloadAndRecoveryPreservesFullInterruptedRecords() throws Exception
    {
        DataGovernanceProperties properties = new DataGovernanceProperties(); properties.setStorageDir(directory.toString());
        var repository = spy(new DataGovernanceFileRunRepository(properties));
        var engine = mock(DataGovernanceEngine.class); var runner = mock(DataGovernanceTestRunner.class);
        var service = new DataGovernanceService(engine, runner, repository);
        var mapper = new ObjectMapper();
        StoredRun queued = richRecord("QUEUED"), running = richRecord("RUNNING"), completed = richRecord("SUCCEEDED");
        try
        {
            repository.save(queued); repository.save(running); repository.save(completed);
            doThrow(new AssertionError("Metadata scans must not materialize all full runs")).when(repository).list();
            for (StoredRun summary : repository.summaries())
            {
                assertEquals(12, summary.ownerId); assertNull(summary.inputJson); assertNull(summary.definition);
                assertTrue(summary.parameters.isEmpty()); assertTrue(summary.run.steps.isEmpty()); assertTrue(summary.run.output.isEmpty());
                assertEquals("frozen-hash", summary.run.definitionHash);
                assertFalse(mapper.writeValueAsString(summary).contains("private-payload"));
            }
            var runs = service.runs(null, 12); // First access recovers interrupted runs through metadata then targeted find.
            assertEquals(3, runs.size()); assertTrue(runs.stream().allMatch(r -> r.steps.isEmpty() && r.output.isEmpty()));
            assertEquals("FAILED", repository.find(queued.run.id).run.status);
            assertEquals("CLEANUP_REQUIRED", repository.find(running.run.id).run.status);
            assertEquals("SUCCEEDED", repository.find(completed.run.id).run.status);
            for (StoredRun original : List.of(queued, running, completed))
            {
                StoredRun restored = repository.find(original.run.id);
                assertEquals(original.inputJson, restored.inputJson); assertEquals(original.definition, restored.definition);
                assertEquals(original.parameters, restored.parameters); assertEquals(original.run.steps, restored.run.steps);
                assertEquals(original.run.output, restored.run.output);
            }
            assertFalse(service.run(running.run.id, 12).steps.isEmpty(), "Detail must remain complete");
            verify(repository, never()).list();
            verifyNoInteractions(runner, engine);
        }
        finally { service.close(); repository.close(); }
    }
    private StoredRun richRecord(String status)
    {
        StoredRun stored = new StoredRun(); stored.ownerId = 12;
        stored.inputJson = "{\"message\":\"private-payload-" + "x".repeat(64 * 1024) + "\"}";
        stored.parameters = Map.of("jsonPath", "$.message");
        stored.definition = new ObjectMapper().valueToTree(Map.of("synthetic", "private-payload-definition"));
        TestRun run = new TestRun(); stored.run = run; run.id = UUID.randomUUID().toString(); run.flowId = UUID.randomUUID().toString();
        run.status = status; run.createdAt = run.updatedAt = Instant.now().toString(); run.definitionHash = "frozen-hash";
        run.steps = List.of(new StepResult("synthetic", "测试", "synthetic", "SUCCEEDED", 1, 1, List.of(), new Samples(List.of(stored.inputJson), List.of("private-payload-step"))));
        run.output = List.of("private-payload-output"); return stored;
    }

}
