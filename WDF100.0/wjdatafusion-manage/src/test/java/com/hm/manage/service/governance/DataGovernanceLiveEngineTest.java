package com.hm.manage.service.governance;

import com.hm.manage.service.governance.DataGovernanceModels.*;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Explicit opt-in only: real isolated engine, no Spring context and no RYNEW business database. */
@EnabledIfSystemProperty(named = "governance.nifi.smoke", matches = "true")
class DataGovernanceLiveEngineTest
{
    @TempDir Path reports;
    @Test void runsCurrentDefinitionInRealNifiAndConfirmsCleanup() throws Exception
    {
        DataGovernanceProperties properties = new DataGovernanceProperties();
        properties.setStorageDir(reports.toString()); properties.setTestTimeoutSeconds(90);
        properties.getNifi().setBaseUrl(System.getProperty("governance.nifi.baseUrl"));
        properties.getNifi().setRootGroupId(System.getProperty("governance.nifi.rootGroupId"));
        properties.getNifi().setCredentialsFile(System.getProperty("governance.nifi.credentialsFile"));
        var client = new DataGovernanceNifiClient(properties); var engine = new DataGovernanceEngine(client, properties);
        var repository = new DataGovernanceFileRunRepository(properties);
        var service = new DataGovernanceService(engine, new DataGovernanceTestRunner(engine), repository);
        try
        {
            Project project = engine.createProject(new CreateProject("API 隔离自检", "真实 NiFi 样本执行验证"));
            Flow flow = engine.createFlow(new CreateFlow(project.id(), "JSON 样本实际执行", DataGovernanceEngine.SAMPLE));
            TestRun run = service.submit(flow.id(), new TestInput("{\"message\":\"synthetic-example\"}", Map.of()), 1);
            long deadline = System.currentTimeMillis() + 120000;
            do { Thread.sleep(250); run = service.run(run.id, 1); }
            while ((run.status.equals("QUEUED") || run.status.equals("RUNNING")) && System.currentTimeMillis() < deadline);
            System.out.println("NiFi smoke: project=" + project.id() + ", flow=" + flow.id() + ", run=" + run.id + ", status=" + run.status + ", cleanup=" + run.cleanupConfirmed);
            assertEquals("SUCCEEDED", run.status, run.error);
            assertTrue(run.cleanupConfirmed); assertFalse(run.output.isEmpty());
            assertTrue(run.output.get(0).contains("synthetic-example")); assertEquals(4, run.steps.size());
            assertTrue(run.steps.stream().anyMatch(step -> step.samples().attributes().stream()
                .anyMatch(attributes -> "synthetic-example".equals(attributes.get("sample.value")))));

            // Change the saved canvas, then prove the new lookup (not a rebuilt default template) was executed.
            for (var processor : engine.groupContents(flow.id()).path("processors"))
            {
                var component = processor.path("component");
                if (component.path("type").asText().endsWith("EvaluateJsonPath"))
                    client.json("PUT", "/processors/" + component.path("id").asText(), DataGovernanceEngine.map(
                        "revision", processor.path("revision"), "component", DataGovernanceEngine.map("id", component.path("id").asText(),
                            "config", DataGovernanceEngine.map("properties", DataGovernanceEngine.map("sample.value", "$.changed")))));
            }
            TestRun changed = await(service, service.submit(flow.id(), new TestInput("{\"message\":\"old\",\"changed\":\"current-canvas\"}", Map.of("requiredValue", "current-canvas")), 1));
            assertEquals("SUCCEEDED", changed.status, changed.error);
            assertTrue(changed.steps.stream().flatMap(step -> step.messages().stream()).anyMatch(message -> message.contains("accepted：1")));
            assertTrue(changed.cleanupConfirmed);

            TestRun cancelled = service.submit(flow.id(), new TestInput("{\"changed\":\"cancel-example\"}", Map.of()), 1);
            long startedDeadline = System.currentTimeMillis() + 10000;
            while (cancelled.engineTestGroupId == null && System.currentTimeMillis() < startedDeadline)
            { Thread.sleep(50); cancelled = service.run(cancelled.id, 1); }
            service.cancel(cancelled.id, 1); cancelled = await(service, cancelled);
            assertEquals("CANCELLED", cancelled.status, cancelled.error); assertTrue(cancelled.cleanupConfirmed);

            engine.createProcessor(flow.id(), "不支持的外部组件", DataGovernanceEngine.STANDARD + "InvokeHTTP", "",
                DataGovernanceEngine.map(), java.util.List.of(), 900);
            TestRun unsupported = await(service, service.submit(flow.id(), new TestInput("{}", Map.of()), 1));
            assertEquals("UNSUPPORTED", unsupported.status); assertTrue(unsupported.cleanupConfirmed);
            assertNull(unsupported.engineTestGroupId); assertTrue(unsupported.steps.isEmpty());
            System.out.println("NiFi smoke verified: current-canvas change, sample attributes, active cancellation, external-node rejection");
            if (engine.supports(DataGovernanceEngine.WRITER))
            {
                Flow textFlow = engine.createFlow(new CreateFlow(project.id(), "协议文本实际执行", DataGovernanceEngine.DELIMITED));
                TestRun text = await(service, service.submit(textFlow.id(), new TestInput("[{\"message\":\"synthetic\",\"picture\":\"\"}]", Map.of()), 1));
                assertEquals("SUCCEEDED", text.status, text.error); assertTrue(text.cleanupConfirmed);
                assertTrue(text.output.get(0).contains("message|\u001fpicture\n"));
                TestRun empty = await(service, service.submit(textFlow.id(), new TestInput("[]", Map.of()), 1));
                assertEquals("EMPTY", empty.status, empty.error); assertTrue(empty.output.isEmpty()); assertTrue(empty.cleanupConfirmed);
                TestRun invalid = await(service, service.submit(textFlow.id(), new TestInput("{\"message\":\"not-array\"}", Map.of()), 1));
                assertEquals("FAILED", invalid.status); assertTrue(invalid.cleanupConfirmed);
                System.out.println("NiFi writer smoke verified: actual 7C1F/header output, explicit EMPTY, failure relation");
            }
        }
        finally { service.close(); repository.close(); }
    }
    private TestRun await(DataGovernanceService service, TestRun run) throws Exception
    {
        long deadline = System.currentTimeMillis() + 120000;
        while ((run.status.equals("QUEUED") || run.status.equals("RUNNING")) && System.currentTimeMillis() < deadline)
        { Thread.sleep(100); run = service.run(run.id, 1); }
        return run;
    }
}
