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

@EnabledIfSystemProperty(named = "governance.nifi.smoke", matches = "true")
class DataGovernanceMultiFlowFileLiveEngineTest
{
    @TempDir Path reports;
    @Test void processesBothWriterFragmentsThroughSerialRunOnceNode() throws Exception
    {
        DataGovernanceProperties properties = new DataGovernanceProperties();
        properties.setStorageDir(reports.toString()); properties.setTestTimeoutSeconds(60);
        properties.getNifi().setBaseUrl(System.getProperty("governance.nifi.baseUrl"));
        properties.getNifi().setRootGroupId(System.getProperty("governance.nifi.rootGroupId"));
        properties.getNifi().setCredentialsFile(System.getProperty("governance.nifi.credentialsFile"));
        var client = new DataGovernanceNifiClient(properties); var engine = new DataGovernanceEngine(client, properties);
        var repository = new DataGovernanceFileRunRepository(properties);
        var service = new DataGovernanceService(engine, new DataGovernanceTestRunner(engine), repository);
        try
        {
            assertTrue(engine.supports(WRITER), "Real protocol Writer must be installed");
            Project project = engine.createProject(new CreateProject("多片 Run Once 实际验证", "75 条记录产生两片，逐片处理到结果队列"));
            Flow flow = engine.createFlow(new CreateFlow(project.id(), "Writer 两片串行处理", "blank"));
            String source = engine.createProcessor(flow.id(), "样本输入", STANDARD + "GenerateFlowFile", "", map("Custom Text", "[]"), List.of(), 0).path("component").path("id").asText();
            String writer = engine.createProcessor(flow.id(), "两片文本", WRITER, "", map("Field Order", "message,picture", "Delimiter Hex", "7C 1F",
                "Include Header", "true", "Split Limit", "75", "Count Basis", "KETTLE_HEADER_INCLUSIVE", "Filename Prefix", "sample"), List.of(), 200).path("component").path("id").asText();
            String attribute = engine.createProcessor(flow.id(), "逐片属性处理", UPDATE, "", map("sample.note", "serial-checked"), List.of(), 400).path("component").path("id").asText();
            String capture = engine.createProcessor(flow.id(), "观察结果", UPDATE, CAPTURE, map(), List.of("success"), 600).path("component").path("id").asText();
            engine.connect(flow.id(), source, writer, List.of("success"));
            engine.connect(flow.id(), writer, attribute, List.of("success"));
            engine.connect(flow.id(), attribute, capture, List.of("success"));
            engine.connect(flow.id(), writer, capture, List.of("failure"));
            engine.connect(flow.id(), writer, capture, List.of("empty"));
            var records = new ObjectMapper().createArrayNode();
            for (int i = 0; i < 75; i++) records.addObject().put("message", "row-" + i).put("picture", "");
            TestRun run = service.submit(flow.id(), new TestInput(records.toString(), Map.of()), 1);
            long deadline = System.currentTimeMillis() + 90000;
            do { Thread.sleep(100); run = service.run(run.id, 1); }
            while (List.of("QUEUED", "RUNNING").contains(run.status) && System.currentTimeMillis() < deadline);
            assertEquals("SUCCEEDED", run.status, run.error); assertTrue(run.cleanupConfirmed);
            assertEquals(2, run.output.size());
            var processed = run.steps.stream().filter(step -> step.id().equals(attribute)).findFirst().orElseThrow();
            assertEquals(2, processed.inputCount()); assertEquals(2, processed.outputCount());
            assertEquals(2, processed.samples().attributes().stream().filter(a -> "serial-checked".equals(a.get("sample.note"))).count());
            String header = "message|\u001fpicture";
            assertTrue(run.output.stream().allMatch(fragment -> fragment.startsWith(header + "\n")));
            assertEquals(75, run.output.stream().mapToLong(fragment -> fragment.lines().filter(line -> !line.equals(header)).count()).sum());
            System.out.println("NiFi multiple-FlowFile smoke: flow=" + flow.id() + ", run=" + run.id + ", fragments=2, attributeInputs=" + processed.inputCount()
                + ", attributeOutputs=" + processed.outputCount() + ", cleanup=" + run.cleanupConfirmed);
        }
        finally { service.close(); repository.close(); }
    }
}
