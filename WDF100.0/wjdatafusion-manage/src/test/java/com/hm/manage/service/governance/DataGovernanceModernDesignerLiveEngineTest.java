package com.hm.manage.service.governance;

import com.hm.common.exception.ServiceException;
import com.hm.manage.service.governance.DataGovernanceModels.*;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import static com.hm.manage.service.governance.DataGovernanceEngine.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "governance.nifi.smoke", matches = "true")
class DataGovernanceModernDesignerLiveEngineTest
{
    @TempDir Path reports;

    @Test void platformDraftCrudUpdatesRealNifiAndIsolatedTestUsesEditedGraph() throws Exception
    {
        DataGovernanceProperties properties = new DataGovernanceProperties(); properties.setStorageDir(reports.toString()); properties.setTestTimeoutSeconds(60);
        properties.getNifi().setBaseUrl(System.getProperty("governance.nifi.baseUrl")); properties.getNifi().setRootGroupId(System.getProperty("governance.nifi.rootGroupId"));
        properties.getNifi().setCredentialsFile(System.getProperty("governance.nifi.credentialsFile"));
        var client = new DataGovernanceNifiClient(properties); var engine = new DataGovernanceEngine(client, properties);
        String marker = "RYNEW_DESIGNER_API_SMOKE:" + UUID.randomUUID();
        String temporaryRoot = engine.createGroup(engine.root(), "平台设计器 API 独立验收", marker).path("component").path("id").asText();
        properties.getNifi().setRootGroupId(temporaryRoot);
        var designer = new DataGovernanceDesigner(engine); var repository = new DataGovernanceFileRunRepository(properties);
        var runner = new DataGovernanceTestRunner(engine); var service = new DataGovernanceService(engine, runner, repository);
        try
        {
            Project project = engine.createProject(new CreateProject("平台编辑器独立验收", "仅使用合成数据；结束自动移除"));
            Flow flow = engine.createFlow(new CreateFlow(project.id(), "拖放式设计 API", "blank"));
            assertTrue(designer.design(flow.id()).editable());
            Map<String, DesignNode> created = new LinkedHashMap<>();
            int column = 0;
            for (DesignNodeType type : designer.nodeTypes())
            {
                DesignNode node = designer.createNode(flow.id(), new CreateDesignNode(type.type(), type.name(), type.role(), new DesignPosition(column++ * 300, 150), null));
                assertTrue(node.editable(), type.id() + ": " + node.issues()); assertEquals(type.role(), node.role());
                created.put(type.id(), node);
            }
            assertEquals(7, designer.design(flow.id()).nodes().size());
            for (String unused : List.of("route", "jolt", "delimited"))
            { DesignNode node = current(designer, flow.id(), created.get(unused).id()); designer.deleteNode(flow.id(), node.id(), node.version()); }
            String source = created.get("source").id(), json = created.get("jsonpath").id(), attributes = created.get("attributes").id(), capture = created.get("capture").id();
            designer.createConnection(flow.id(), new CreateDesignConnection(source, json, "success"));
            designer.createConnection(flow.id(), new CreateDesignConnection(json, attributes, "matched"));
            designer.createConnection(flow.id(), new CreateDesignConnection(json, capture, "unmatched"));
            designer.createConnection(flow.id(), new CreateDesignConnection(json, capture, "failure"));
            designer.createConnection(flow.id(), new CreateDesignConnection(attributes, capture, "success"));
            DesignNode old = current(designer, flow.id(), json);
            var values = new LinkedHashMap<>(old.properties()); values.put("sample.value", "$.city"); values.put("sample.remove", "$.message");
            DesignNode updated = designer.updateNode(flow.id(), json, new UpdateDesignNode(old.version(), "提取城市", new DesignPosition(440, 280), values));
            assertEquals(new DesignPosition(440, 280), updated.position());
            assertEquals(409, assertThrows(ServiceException.class, () -> designer.updateNode(flow.id(), json, new UpdateDesignNode(old.version(), "旧版本", null, null))).getCode());
            values.remove("sample.remove"); updated = designer.updateNode(flow.id(), json, new UpdateDesignNode(updated.version(), null, null, values));
            assertFalse(updated.properties().containsKey("sample.remove"));
            var transform = current(designer, flow.id(), attributes);
            designer.updateNode(flow.id(), attributes, new UpdateDesignNode(transform.version(), "城市转大写", null, Map.of("sample.result", "${sample.value:toUpper()}")));
            Design graph = designer.design(flow.id()); assertTrue(graph.editable(), graph.issues().toString()); assertEquals(4, graph.nodes().size()); assertEquals(5, graph.connections().size());
            assertThrows(ServiceException.class, () -> designer.deleteNode(flow.id(), source, current(designer, flow.id(), source).version()));

            TestRun run = service.submit(flow.id(), new TestInput("{\"message\":\"synthetic\",\"city\":\"shanghai\"}", Map.of()), 1);
            long deadline = System.currentTimeMillis() + 90000;
            while (List.of("QUEUED", "RUNNING").contains(run.status) && System.currentTimeMillis() < deadline) { Thread.sleep(100); run = service.run(run.id, 1); }
            assertEquals("SUCCEEDED", run.status, run.error); assertTrue(run.cleanupConfirmed); assertEquals(64, run.definitionHash.length());
            StepResult step = run.steps.stream().filter(s -> s.id().equals(attributes)).findFirst().orElseThrow();
            assertTrue(step.samples().attributes().stream().anyMatch(a -> "SHANGHAI".equals(a.get("sample.result"))), "The test must use the graph edited through the platform API");
            for (DesignConnection edge : designer.design(flow.id()).connections()) designer.deleteConnection(flow.id(), edge.id(), edge.version());
            for (DesignNode node : designer.design(flow.id()).nodes()) designer.deleteNode(flow.id(), node.id(), node.version());
            assertTrue(designer.design(flow.id()).nodes().isEmpty()); assertTrue(designer.design(flow.id()).connections().isEmpty());
            System.out.println("Modern designer real NiFi: seven node types, real CRUD/position/revision/dynamic removal, edited sample SHANGHAI, test cleanup confirmed");
        }
        finally
        {
            service.close(); repository.close();
            var owned = client.json("GET", "/process-groups/" + temporaryRoot, null);
            assertEquals(marker, owned.path("component").path("comments").asText());
            client.json("DELETE", "/process-groups/" + temporaryRoot + "?version=" + owned.path("revision").path("version").asLong() + "&disconnectedNodeAcknowledged=false", null);
            assertTrue(assertThrows(ServiceException.class, () -> client.json("GET", "/process-groups/" + temporaryRoot, null)).getMessage().contains("404"));
            System.out.println("Modern designer temporary root removed: " + temporaryRoot);
        }
    }

    private DesignNode current(DataGovernanceDesigner designer, String flow, String id)
    { return designer.design(flow).nodes().stream().filter(n -> n.id().equals(id)).findFirst().orElseThrow(); }
}
