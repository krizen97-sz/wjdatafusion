package com.hm.manage.service.governance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hm.manage.service.governance.DataGovernanceModels.CreateFlow;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static com.hm.manage.service.governance.DataGovernanceEngine.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DataGovernanceDesignerTest
{
    private final ObjectMapper mapper = new ObjectMapper();
    private final String root = "00000000-0000-0000-0000-000000000001";
    private final String project = "00000000-0000-0000-0000-000000000002";
    private final String flow = "00000000-0000-0000-0000-000000000003";
    private final List<JsonNode> processors = new ArrayList<>();
    private final List<JsonNode> connections = new ArrayList<>();

    private DataGovernanceEngine engine(String designerPath)
    {
        var properties = new DataGovernanceProperties();
        properties.getNifi().setRootGroupId(root);
        properties.getNifi().setDesignerPath(designerPath);
        var client = mock(DataGovernanceNifiClient.class);
        when(client.configured()).thenReturn(true);
        ObjectNode flowEntity = mapper.valueToTree(map("component", map("id", flow, "parentGroupId", project,
            "name", "test", "comments", FLOW + "blank")));
        when(client.json(anyString(), anyString(), any())).thenAnswer(invocation -> {
            String method = invocation.getArgument(0), path = invocation.getArgument(1);
            if (method.equals("GET") && path.equals("/process-groups/" + root + "/process-groups"))
                return mapper.valueToTree(map("processGroups", List.of(map("component", map("id", project,
                    "name", "project", "comments", PROJECT)))));
            if (method.equals("POST") && path.equals("/process-groups/" + project + "/process-groups"))
            {
                JsonNode request = mapper.valueToTree(invocation.getArgument(2));
                ((ObjectNode) flowEntity.path("component")).put("comments", request.path("component").path("comments").asText());
                return flowEntity;
            }
            if (method.equals("GET") && path.equals("/process-groups/" + flow)) return flowEntity;
            if (method.equals("GET") && path.equals("/flow/processor-types"))
                return mapper.valueToTree(map("processorTypes", List.of(STANDARD + "GenerateFlowFile", STANDARD + "EvaluateJsonPath",
                    STANDARD + "RouteOnAttribute", UPDATE, WRITER).stream().map(type -> map("type", type,
                    "bundle", type.startsWith("com.hm.governance.") ? map("group", "com.hm.governance", "artifact", "governance-nifi-nar", "version", CURRENT_COMPATIBILITY_BUNDLE) : map("group", "test", "artifact", "test-nar", "version", "2.11.0"))).toList()));
            if (method.equals("POST") && (path.equals("/process-groups/" + flow + "/processors")
                || path.equals("/process-groups/" + flow + "/connections")))
            {
                ObjectNode entity = mapper.valueToTree(invocation.getArgument(2));
                ((ObjectNode) entity.path("component")).put("id", UUID.randomUUID().toString());
                (path.endsWith("/processors") ? processors : connections).add(entity);
                return entity;
            }
            throw new AssertionError("Unexpected request " + method + " " + path);
        });
        return new DataGovernanceEngine(client, properties);
    }

    @Test void designerLinksUseNifiHashRouteAndNormalizeTrailingSlashes()
    {
        for (String path : List.of("/nifi", "/nifi/", "/nifi///", "/gateway/nifi/"))
        {
            var result = engine(path).createFlow(new CreateFlow(project, "test", "blank"));
            URI link = URI.create(result.designerPath());
            assertEquals(path.replaceAll("/+$", "") + "/", link.getPath());
            assertEquals("/process-groups/" + flow, link.getFragment());
            assertNull(link.getQuery());
        }
    }

    @Test void sampleTemplateSeparatesCardsAndParallelRelationshipsWithoutChangingSafeExecution()
    {
        engine("/nifi/").createFlow(new CreateFlow(project, "test", SAMPLE));
        assertLayout(4, 6);
        assertRelations("提取字段", "查看测试结果", Set.of("unmatched", "failure"));
        assertRelations("条件路由", "查看测试结果", Set.of("accepted", "unmatched"));
        assertSafeSnapshotIgnoresLayout();
    }

    @Test void writerTemplateKeepsDistinctSuccessEmptyFailureBranches()
    {
        engine("/nifi/").createFlow(new CreateFlow(project, "test", DELIMITED));
        assertLayout(3, 4);
        assertRelations("协议文本输出", "查看测试结果", Set.of("success", "empty", "failure"));
        assertSafeSnapshotIgnoresLayout();
    }

    private void assertLayout(int nodeCount, int edgeCount)
    {
        assertEquals(nodeCount, processors.size()); assertEquals(edgeCount, connections.size());
        List<Double> columns = processors.stream().map(p -> p.path("component").path("position").path("x").asDouble()).sorted().toList();
        for (int i = 1; i < columns.size(); i++) assertTrue(columns.get(i) - columns.get(i - 1) >= 500,
            "350 px processor cards need enough separation for connection labels");
        Set<String> drawnPaths = new HashSet<>();
        for (JsonNode entity : connections)
        {
            JsonNode c = entity.path("component");
            assertEquals(1, c.path("selectedRelationships").size(), "The isolated runner requires an explicit relationship per connection");
            assertTrue(drawnPaths.add(c.path("source").path("id").asText() + "/" + c.path("destination").path("id").asText()
                + "/" + c.path("bends")), "Parallel connections must have distinct paths");
            for (JsonNode bend : c.path("bends"))
                assertTrue(bend.path("y").asDouble() < 0 || bend.path("y").asDouble() >= 400, "Branch lanes must clear processor cards");
        }
    }

    private void assertRelations(String sourceName, String targetName, Set<String> expected)
    {
        String from = nodeId(sourceName), to = nodeId(targetName);
        Set<String> actual = new HashSet<>();
        for (JsonNode entity : connections)
        {
            JsonNode c = entity.path("component");
            if (from.equals(c.path("source").path("id").asText()) && to.equals(c.path("destination").path("id").asText()))
                actual.add(c.path("selectedRelationships").get(0).asText());
        }
        assertEquals(expected, actual);
    }

    private String nodeId(String name)
    { return processors.stream().map(p -> p.path("component")).filter(p -> name.equals(p.path("name").asText())).findFirst().orElseThrow().path("id").asText(); }

    private void assertSafeSnapshotIgnoresLayout()
    {
        JsonNode graph = mapper.valueToTree(map("processors", processors, "connections", connections));
        JsonNode snapshot = new DataGovernanceSafeFlow(graph).freeze("{\"message\":\"sample\"}", Map.of());
        for (JsonNode node : graph.path("processors")) ((ObjectNode) node.path("component")).remove("position");
        for (JsonNode edge : graph.path("connections")) ((ObjectNode) edge.path("component")).remove("bends");
        assertEquals(DataGovernanceSafeFlow.hash(snapshot),
            DataGovernanceSafeFlow.hash(new DataGovernanceSafeFlow(graph).freeze("{\"message\":\"sample\"}", Map.of())));
    }
}
