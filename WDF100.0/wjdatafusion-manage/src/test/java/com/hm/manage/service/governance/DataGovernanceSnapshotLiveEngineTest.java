package com.hm.manage.service.governance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hm.manage.service.governance.DataGovernanceModels.*;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.hm.manage.service.governance.DataGovernanceEngine.*;

@EnabledIfSystemProperty(named = "governance.nifi.smoke", matches = "true")
class DataGovernanceSnapshotLiveEngineTest
{
    @TempDir Path reports;
    @Test void queuedTestUsesSubmitSnapshotAfterCanvasIsChanged() throws Exception
    {
        DataGovernanceProperties properties = new DataGovernanceProperties(); properties.setStorageDir(reports.toString()); properties.setTestTimeoutSeconds(60);
        properties.getNifi().setBaseUrl(System.getProperty("governance.nifi.baseUrl")); properties.getNifi().setRootGroupId(System.getProperty("governance.nifi.rootGroupId"));
        properties.getNifi().setCredentialsFile(System.getProperty("governance.nifi.credentialsFile"));
        var client = new DataGovernanceNifiClient(properties); var engine = new DataGovernanceEngine(client, properties);
        var repository = new DataGovernanceFileRunRepository(properties); var runner = spy(new DataGovernanceTestRunner(engine));
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        doAnswer(call -> { entered.countDown(); assertTrue(release.await(20, TimeUnit.SECONDS)); return call.callRealMethod(); })
            .when(runner).execute(any(), any(), any());
        var service = new DataGovernanceService(engine, runner, repository);
        try
        {
            Project project = engine.createProject(new CreateProject("提交快照实际验证", "排队后修改 NiFi 画布不改变已提交测试"));
            Flow flow = engine.createFlow(new CreateFlow(project.id(), "队列快照 Jolt", "blank"));
            String source = engine.createProcessor(flow.id(), "样本输入", STANDARD + "GenerateFlowFile", "", map("Custom Text", "original-canvas-text"), List.of(), 0).path("component").path("id").asText();
            String jolt = engine.createProcessor(flow.id(), "Jolt", JOLT, "", map("Jolt Specification", spec("submitted")), List.of(), 200).path("component").path("id").asText();
            String capture = engine.createProcessor(flow.id(), "观察结果", UPDATE, CAPTURE, map(), List.of("success"), 400).path("component").path("id").asText();
            engine.connect(flow.id(), source, jolt, List.of("success"));
            engine.connect(flow.id(), jolt, capture, List.of("success")); engine.connect(flow.id(), jolt, capture, List.of("failure"));
            TestInput sample = new TestInput("{\"message\":\"synthetic\"}", Map.of());
            TestRun first = service.submit(flow.id(), sample, 1);
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            TestRun queued = service.submit(flow.id(), sample, 1);
            assertEquals("QUEUED", queued.status); assertNotNull(queued.definitionCapturedAt); assertEquals(64, queued.definitionHash.length());
            String submittedHash = queued.definitionHash;
            var current = client.json("GET", "/processors/" + jolt, null);
            client.json("PUT", "/processors/" + jolt, map("revision", current.path("revision"), "component", map("id", jolt,
                "config", map("properties", map("Jolt Specification", spec("changed-after-submit"))))));
            release.countDown();
            first = await(service, first); queued = await(service, queued);
            assertEquals("SUCCEEDED", first.status, first.error); assertEquals("SUCCEEDED", queued.status, queued.error);
            assertTrue(first.cleanupConfirmed); assertTrue(queued.cleanupConfirmed);
            assertEquals(submittedHash, queued.definitionHash);
            var output = new ObjectMapper().readTree(queued.output.get(0)); assertEquals("submitted", output.path("version").asText());
            assertFalse(queued.output.get(0).contains("changed-after-submit"));
            assertFalse(repository.find(queued.id).definition.toString().contains("original-canvas-text"));
            System.out.println("NiFi submit-snapshot smoke: flow=" + flow.id() + ", run=" + queued.id + ", definitionHash=" + queued.definitionHash + ", cleanup=" + queued.cleanupConfirmed);
        }
        finally { release.countDown(); service.close(); repository.close(); }
    }
    private static String spec(String version) { return "[{\"operation\":\"default\",\"spec\":{\"version\":\"" + version + "\"}}]"; }
    private TestRun await(DataGovernanceService service, TestRun run) throws Exception
    {
        long deadline = System.currentTimeMillis() + 90000;
        while (List.of("QUEUED", "RUNNING").contains(run.status) && System.currentTimeMillis() < deadline)
        { Thread.sleep(100); run = service.run(run.id, 1); }
        return run;
    }
}
