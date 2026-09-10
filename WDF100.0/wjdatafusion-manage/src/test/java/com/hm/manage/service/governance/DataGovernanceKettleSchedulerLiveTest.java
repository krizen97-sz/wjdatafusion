package com.hm.manage.service.governance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static com.hm.manage.service.governance.DataGovernanceKettleService.*;
import static com.hm.manage.service.governance.DataGovernanceKettleScheduler.*;

/** Real one-second coordinator and original engine; the plan is paused after its first durable intent. */
@EnabledIfSystemProperty(named="kettle.cronLive", matches="true")
class DataGovernanceKettleSchedulerLiveTest
{
    @TempDir Path temporary;
    @Test void shortCronRunsFrozenChineseCsvThroughNativeEngineThenRemainsPaused() throws Exception
    {
        DataGovernanceKettleProperties p = new DataGovernanceKettleProperties(); p.setEnabled(true);
        p.setStorageDir(temporary.toString()); p.setWorkerTokenFile(System.getProperty("kettle.tokenFile"));
        DataGovernanceKettleClient client = new DataGovernanceKettleClient(p);
        DataGovernanceKettleService service = new DataGovernanceKettleService(p, DataGovernanceKettleApiTest.crypto(), client);
        DataGovernanceKettleScheduler scheduler = new DataGovernanceKettleScheduler(service, p);
        try
        {
            JsonNode capabilities = client.request("GET", "/capabilities", null);
            String xml = DataGovernanceKettleLiveTest.fixture(capabilities, "定时输入.csv");
            String definition = (String)service.save(null, new DefinitionInput("定时原生合成验收", null, DataGovernanceKettleXml.encode(xml)), 73).get("id");
            service.upload(definition, "定时输入.csv", new ByteArrayInputStream("name\nalice\nbob\n".getBytes(StandardCharsets.UTF_8)), 73);
            View plan = scheduler.create(definition, new Input("短周期原生验收", null, 1L, "*/2 * * * * ?", "Asia/Shanghai"), 73);
            scheduler.start(); scheduler.state(plan.id(), new StateInput(true, 1L), 73);
            long deadline = System.nanoTime() + 60_000_000_000L;
            while (plan.lastRunId() == null && System.nanoTime() < deadline) { Thread.sleep(100); plan = scheduler.list(definition, 73).get(0); }
            assertNotNull(plan.lastRunId()); String runId = plan.lastRunId();
            scheduler.state(plan.id(), new StateInput(false, plan.revision()), 73);
            do { Thread.sleep(100); plan = scheduler.list(definition, 73).get(0); }
            while (plan.activeRunId() != null && !plan.recoveryRequired() && System.nanoTime() < deadline);
            assertEquals("SUCCEEDED", plan.lastRunState()); assertFalse(plan.enabled()); assertNull(plan.activeRunId()); assertEquals("PAUSED", plan.status());
            Map<String,Object> run = service.run(runId, 73); boolean downloaded = false;
            for (JsonNode file : (JsonNode)run.get("files")) if (file.path("role").asText().equals("output"))
                try (var download = service.download(runId, file.path("name").asText(), 73))
                { String data = new String(download.stream().readAllBytes(), StandardCharsets.UTF_8); assertTrue(data.contains("ALICE!")); assertTrue(data.contains("BOB!")); downloaded = true; }
            assertTrue(downloaded); assertEquals(1, service.runs(definition, 73).size());
            String evidence = System.getProperty("kettle.cronEvidence");
            if (evidence != null)
            { Path path = Path.of(evidence); Files.createDirectories(path.getParent()); new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(path.toFile(),
                Map.of("schedule", plan, "runId", runId, "state", run.get("state"), "files", run.get("files"), "nativeFileContentVerified", true, "runCount", 1)); }
        }
        finally { scheduler.close(); service.close(); }
    }
}
