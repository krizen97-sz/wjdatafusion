package com.hm.manage.service.governance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hm.manage.service.governance.DataGovernanceModels.*;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static com.hm.manage.service.governance.DataGovernanceEngine.*;

/** Real NiFi 2.11 FQN and defaults; no application context or business datasource. */
@EnabledIfSystemProperty(named = "governance.nifi.smoke", matches = "true")
class DataGovernanceJoltLiveEngineTest
{
    @TempDir Path reports;
    @Test void executesSavedJoltChainWithShiftAndDefault() throws Exception
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
            String realType = "org.apache.nifi.processors.jolt.JoltTransformJSON";
            assertTrue(engine.supports(realType), "NiFi must register the real Jolt processor");
            Project project = engine.createProject(new CreateProject("Jolt 实际组件验证", "保存画布 Chain 后运行真实样本"));
            Flow flow = engine.createFlow(new CreateFlow(project.id(), "Jolt shift/default", "blank"));
            String source = engine.createProcessor(flow.id(), "样本输入", STANDARD + "GenerateFlowFile", "", map("Custom Text", "{}"), List.of(), 0).path("component").path("id").asText();
            String initialSpec = "[{\"operation\":\"shift\",\"spec\":{\"message\":\"renamed\"}}]";
            var jolt = engine.createProcessor(flow.id(), "Jolt 映射", realType, "", map("Jolt Specification", initialSpec), List.of(), 250);
            String joltId = jolt.path("component").path("id").asText();
            String capture = engine.createProcessor(flow.id(), "观察结果", UPDATE, CAPTURE, map(), List.of("success"), 500).path("component").path("id").asText();
            engine.connect(flow.id(), source, joltId, List.of("success"));
            engine.connect(flow.id(), joltId, capture, List.of("success"));
            engine.connect(flow.id(), joltId, capture, List.of("failure"));

            var current = client.json("GET", "/processors/" + joltId, null);
            assertEquals("jolt-transform-chain", current.path("component").path("config").path("properties").path("Jolt Transform").asText());
            String savedSpec = "[{\"operation\":\"shift\",\"spec\":{\"message\":\"renamed\"}},{\"operation\":\"default\",\"spec\":{\"origin\":\"saved-canvas\"}}]";
            client.json("PUT", "/processors/" + joltId, map("revision", current.path("revision"), "component",
                map("id", joltId, "config", map("properties", map("Jolt Specification", savedSpec)))));
            TestRun run = service.submit(flow.id(), new TestInput("{\"message\":\"合成样本\"}", Map.of()), 1);
            long deadline = System.currentTimeMillis() + 120000;
            do { Thread.sleep(150); run = service.run(run.id, 1); }
            while (List.of("QUEUED", "RUNNING").contains(run.status) && System.currentTimeMillis() < deadline);
            assertEquals("SUCCEEDED", run.status, run.error); assertTrue(run.cleanupConfirmed);
            assertEquals(1, run.output.size());
            var output = new ObjectMapper().readTree(run.output.get(0));
            assertEquals("合成样本", output.path("renamed").asText()); assertEquals("saved-canvas", output.path("origin").asText());
            assertFalse(output.has("message"));
            var step = run.steps.stream().filter(s -> s.type().equals(realType)).findFirst().orElseThrow();
            assertEquals(1, step.inputCount()); assertEquals(1, step.outputCount());
            assertFalse(step.samples().output().isEmpty());
            System.out.println("NiFi Jolt smoke: project=" + project.id() + ", flow=" + flow.id() + ", run=" + run.id + ", status=" + run.status + ", cleanup=" + run.cleanupConfirmed);
        }
        finally { service.close(); repository.close(); }
    }
}
