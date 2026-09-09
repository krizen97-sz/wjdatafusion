package com.hm.manage.service.governance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hm.common.exception.ServiceException;
import com.hm.manage.service.governance.DataGovernanceModels.*;
import com.hm.manage.service.governance.DataGovernanceScheduleModels.*;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import static com.hm.manage.service.governance.DataGovernanceEngine.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "governance.nifi.smoke", matches = "true")
class DataGovernanceScheduleLiveEngineTest
{
    @TempDir Path reports;
    @Test void cronRunsPublishedDefinitionAfterCanvasChangesAndTracksActualTerminalCleanup() throws Exception
    {
        DataGovernanceProperties properties = new DataGovernanceProperties(); properties.setStorageDir(reports.toString()); properties.setTestTimeoutSeconds(60);
        properties.getNifi().setBaseUrl(System.getProperty("governance.nifi.baseUrl")); properties.getNifi().setRootGroupId(System.getProperty("governance.nifi.rootGroupId"));
        properties.getNifi().setCredentialsFile(System.getProperty("governance.nifi.credentialsFile"));
        var client = new DataGovernanceNifiClient(properties); var engine = new DataGovernanceEngine(client, properties);
        String marker = "RYNEW_SCHEDULER_SMOKE:" + UUID.randomUUID();
        String temporaryRoot = engine.createGroup(engine.root(), "固定批次调度独立验收", marker).path("component").path("id").asText();
        properties.getNifi().setRootGroupId(temporaryRoot);
        var repository = new DataGovernanceFileRunRepository(properties); var store = new DataGovernanceScheduleStore(properties);
        var service = new DataGovernanceService(engine, new DataGovernanceTestRunner(engine), repository);
        var clock = new DataGovernanceSchedulerTest.MutableClock("2026-09-09T00:00:00Z");
        var scheduler = new DataGovernanceScheduler(service, store, client, clock);
        try
        {
            Project project = engine.createProject(new CreateProject("发布与调度独立验收", "合成数据，验收后删除"));
            Flow flow = engine.createFlow(new CreateFlow(project.id(), "冻结 Jolt 版本", "blank"));
            String source = engine.createProcessor(flow.id(), "固定 JSON 批次", STANDARD + "GenerateFlowFile", "", map(), List.of(), 0).path("component").path("id").asText();
            String jolt = engine.createProcessor(flow.id(), "补充版本字段", JOLT, "", map("Jolt Specification", spec("published-v1")), List.of(), 220).path("component").path("id").asText();
            String capture = engine.createProcessor(flow.id(), "观察结果", UPDATE, CAPTURE, map(), List.of("success"), 440).path("component").path("id").asText();
            engine.connect(flow.id(), source, jolt, List.of("success"));
            engine.connect(flow.id(), jolt, capture, List.of("success")); engine.connect(flow.id(), jolt, capture, List.of("failure"));
            ReleaseSummary published = scheduler.publish(new PublishRequest(flow.id(), "已发布版本一", "{\"message\":\"synthetic\"}", Map.of()), 7);
            assertEquals(1, published.version());
            var current = client.json("GET", "/processors/" + jolt, null);
            client.json("PUT", "/processors/" + jolt, map("revision", current.path("revision"), "component", map("id", jolt,
                "config", map("properties", map("Jolt Specification", spec("draft-v2"))))));
            ReleaseSummary second = scheduler.publish(new PublishRequest(flow.id(), "已发布版本二", "{\"message\":\"synthetic\"}", Map.of()), 7);
            assertEquals(2, second.version()); assertNotEquals(published.definitionHash(), second.definitionHash());
            ScheduleSummary plan = scheduler.create(new ScheduleRequest("固定批次每分钟", published.id(), "0 * * * * ?", "Asia/Shanghai", null), 7);
            assertFalse(plan.enabled());
            plan = scheduler.state(plan.id(), new StateRequest(true, plan.revision()), 7);
            clock.set("2026-09-09T00:01:00Z"); scheduler.tick();
            plan = scheduler.schedules(7, null).get(0); assertNotNull(plan.lastRunId());
            TestRun first = await(service, plan.lastRunId()); scheduler.tick();
            assertEquals("SUCCEEDED", first.status, first.error); assertTrue(first.cleanupConfirmed);
            assertEquals(published.definitionHash(), first.definitionHash);
            assertEquals("published-v1", new ObjectMapper().readTree(first.output.get(0)).path("version").asText());
            plan = scheduler.schedules(7, null).get(0); assertNull(plan.activeRunId()); assertEquals("SUCCEEDED", plan.lastRunStatus());
            plan = scheduler.state(plan.id(), new StateRequest(false, plan.revision()), 7);
            clock.set("2026-09-09T00:10:00Z"); scheduler.tick();
            assertEquals(first.id, scheduler.schedules(7, null).get(0).lastRunId(), "paused plan must not run");
            plan = scheduler.update(plan.id(), new ScheduleRequest("新版固定批次", second.id(), plan.cron(), plan.timeZone(), plan.revision()), 7);
            TestRun submitted = scheduler.runNow(plan.id(), 7); TestRun next = await(service, submitted.id); scheduler.tick();
            assertEquals("SUCCEEDED", next.status, next.error); assertTrue(next.cleanupConfirmed);
            assertEquals("draft-v2", new ObjectMapper().readTree(next.output.get(0)).path("version").asText());
            assertTrue(scheduler.releases(8, null).isEmpty());
            System.out.println("Governance scheduler real NiFi: cron=v1, edited canvas=v2, manual rebind=v2, paused trigger suppressed, real terminal + cleanup, hashes=" + published.definitionHash() + "," + second.definitionHash());
        }
        finally
        {
            scheduler.close(); service.close(); store.close(); repository.close();
            var owned = client.json("GET", "/process-groups/" + temporaryRoot, null);
            assertEquals(marker, owned.path("component").path("comments").asText());
            client.json("DELETE", "/process-groups/" + temporaryRoot + "?version=" + owned.path("revision").path("version").asLong() + "&disconnectedNodeAcknowledged=false", null);
            assertTrue(assertThrows(ServiceException.class, () -> client.json("GET", "/process-groups/" + temporaryRoot, null)).getMessage().contains("404"));
            System.out.println("Governance scheduler isolated root removed: " + temporaryRoot);
        }
    }
    private static String spec(String version) { return "[{\"operation\":\"default\",\"spec\":{\"version\":\"" + version + "\"}}]"; }
    private TestRun await(DataGovernanceService service, String id) throws Exception
    {
        long deadline = System.currentTimeMillis() + 90000; TestRun run = service.run(id, 7);
        while (List.of("QUEUED", "RUNNING").contains(run.status) && System.currentTimeMillis() < deadline)
        { Thread.sleep(100); run = service.run(id, 7); }
        return run;
    }
}
