package com.hm.manage.service.governance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hm.common.annotation.Log;
import com.hm.common.exception.ServiceException;
import com.hm.manage.controller.DataGovernanceController;
import com.hm.manage.service.governance.DataGovernanceModels.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import static com.hm.manage.service.governance.DataGovernanceEngine.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DataGovernanceModernDesignerTest
{
    private final ObjectMapper mapper = new ObjectMapper();
    private final String flow = UUID.randomUUID().toString();
    private final Map<String, JsonNode> nodes = new LinkedHashMap<>(), edges = new LinkedHashMap<>();
    private final List<JsonNode> services = new ArrayList<>();
    private DataGovernanceNifiClient client;
    private DataGovernanceEngine engine;
    private DataGovernanceDesigner designer;
    private int mutations;

    @BeforeEach void setup()
    {
        client = mock(DataGovernanceNifiClient.class);
        engine = spy(new DataGovernanceEngine(client, new DataGovernanceProperties()));
        doReturn(new Flow(flow, "project", "design", "", flow, "blank", "")).when(engine).flow(flow);
        doAnswer(call -> mapper.valueToTree(map("processors", nodes.values(), "connections", edges.values()))).when(engine).groupContents(flow);
        designer = new DataGovernanceDesigner(engine);
        when(client.json(anyString(), anyString(), any())).thenAnswer(call -> {
            String method = call.getArgument(0), path = call.getArgument(1);
            if (method.equals("GET") && path.contains("/controller-services?")) return mapper.valueToTree(map("controllerServices", services));
            if (method.equals("GET") && path.equals("/flow/processor-types")) return mapper.valueToTree(map("processorTypes", designer.nodeTypes().stream().map(t -> map("type", t.type(), "bundle", map("group", "test", "artifact", "test-nar", "version", "2.11.0"))).toList()));
            mutations++;
            if (method.equals("POST") && path.endsWith("/processors"))
            {
                ObjectNode entity = mapper.valueToTree(call.getArgument(2)); ObjectNode p = (ObjectNode) entity.path("component"); String id = UUID.randomUUID().toString();
                p.put("id", id); p.put("parentGroupId", flow); p.put("state", "STOPPED"); p.put("validationStatus", "INVALID");
                p.set("relationships", mapper.valueToTree(List.of(map("name", "success"))));
                entity.set("status", mapper.valueToTree(map("aggregateSnapshot", map("activeThreadCount", 0)))); nodes.put(id, entity); return entity;
            }
            if (method.equals("PUT") && path.startsWith("/processors/"))
            {
                String id = path.substring("/processors/".length()); ObjectNode entity = (ObjectNode) nodes.get(id); JsonNode request = mapper.valueToTree(call.getArgument(2)); ObjectNode p = (ObjectNode) entity.path("component");
                request.path("component").fields().forEachRemaining(e -> {
                    if (e.getKey().equals("config")) e.getValue().path("properties").fields().forEachRemaining(prop -> { ObjectNode values = (ObjectNode) p.path("config").path("properties"); if (prop.getValue().isNull()) values.remove(prop.getKey()); else values.set(prop.getKey(), prop.getValue()); });
                    else p.set(e.getKey(), e.getValue());
                });
                ((ObjectNode) entity.path("revision")).put("version", request.path("revision").path("version").asLong() + 1); return entity;
            }
            if (method.equals("DELETE")) return mapper.createObjectNode();
            throw new AssertionError("Unexpected request " + method + " " + path);
        });
    }

    @Test void safeDraftCreationUsesRealProcessorEntityAndPosition()
    {
        var type = type("jsonpath");
        DesignNode created = designer.createNode(flow, new CreateDesignNode(type.type(), "提取城市", "PROCESSOR", new DesignPosition(250, 180), Map.of("sample.city", "$.city")));
        assertEquals(new DesignPosition(250, 180), created.position()); assertTrue(created.editable());
        assertEquals("$.city", created.properties().get("sample.city")); assertEquals("flowfile-attribute", created.properties().get("Destination"));
        assertTrue(designer.design(flow).editable()); assertEquals(1, mutations);
        verify(engine, times(2)).flow(flow);
    }

    @Test void sourceAndCaptureAreUniqueAndCaptureRoleCannotChange()
    {
        create("source"); assertThrows(ServiceException.class, () -> create("source"));
        DesignNode capture = create("capture"); assertEquals("CAPTURE", capture.role()); assertTrue(capture.relationships().isEmpty());
        assertThrows(ServiceException.class, () -> create("capture"));
        assertThrows(ServiceException.class, () -> designer.createNode(flow, new CreateDesignNode(STANDARD + "EvaluateJsonPath", "bad", "CAPTURE", new DesignPosition(0, 0), Map.of())));
        assertEquals(2, mutations);
    }

    @Test void positionOnlyUpdatePreservesPropertiesAndStaleRevisionNeverMutates()
    {
        DesignNode node = create("attributes"); Map<String, String> original = new LinkedHashMap<>(node.properties());
        DesignNode moved = designer.updateNode(flow, node.id(), new UpdateDesignNode(0L, null, new DesignPosition(720, 260), null));
        assertEquals(1, moved.version()); assertEquals(original, moved.properties());
        var conflict = assertThrows(ServiceException.class, () -> designer.updateNode(flow, node.id(), new UpdateDesignNode(0L, "stale", null, null)));
        assertEquals(409, conflict.getCode()); assertEquals(2, mutations);
        assertThrows(ServiceException.class, () -> designer.updateNode(flow, node.id(), new UpdateDesignNode(1L, null, new DesignPosition(Double.NaN, 0), null)));
        assertEquals(2, mutations);
    }

    @Test void replacingPropertiesClearsRemovedDynamicAttributes()
    {
        DesignNode node = create("attributes");
        DesignNode saved = designer.updateNode(flow, node.id(), new UpdateDesignNode(0L, null, null, Map.of("sample.new", "literal")));
        assertFalse(saved.properties().containsKey("sample.result")); assertEquals("literal", saved.properties().get("sample.new"));
    }

    @Test void rejectsExternalExpressionsUnknownPluginAndSensitiveProperties()
    {
        DesignNode node = create("attributes"); int baseline = mutations;
        for (String value : List.of("${env('HOME')}", "#{database.password}", "${sample.x:append(${env('TOKEN')})}"))
            assertThrows(ServiceException.class, () -> designer.updateNode(flow, node.id(), new UpdateDesignNode(0L, null, null, Map.of("sample.value", value))));
        assertThrows(ServiceException.class, () -> designer.createNode(flow, new CreateDesignNode(STANDARD + "InvokeHTTP", "external", "PROCESSOR", new DesignPosition(0, 0), Map.of())));
        assertThrows(ServiceException.class, () -> designer.updateNode(flow, node.id(), new UpdateDesignNode(0L, null, null, Map.of("password", "should-not-be-saved"))));
        assertEquals(baseline, mutations);
    }

    @Test void unsafeNativeConfigurationIsReadOnlyAndDoesNotLeakProperties()
    {
        DesignNode node = create("attributes"); ObjectNode p = (ObjectNode) nodes.get(node.id()).path("component");
        ((ObjectNode) p.path("config").path("properties")).put("sample.value", "${env('PRIVATE_TOKEN')}");
        Design design = designer.design(flow); assertFalse(design.editable()); assertFalse(design.nodes().get(0).editable()); assertTrue(design.nodes().get(0).properties().isEmpty());
        assertFalse(mapper.valueToTree(design).toString().contains("PRIVATE_TOKEN"));
        assertThrows(ServiceException.class, () -> designer.deleteNode(flow, node.id(), 0L)); assertEquals(1, mutations);
    }

    @Test void nativeSensitiveDescriptorAndAdvancedRulesAreNotEditable()
    {
        DesignNode node = create("attributes"); ObjectNode config = (ObjectNode) nodes.get(node.id()).path("component").path("config");
        config.set("descriptors", mapper.valueToTree(map("sample.result", map("sensitive", true))));
        assertFalse(designer.design(flow).editable()); assertTrue(designer.design(flow).nodes().get(0).properties().isEmpty());
        config.remove("descriptors"); config.put("annotationData", "advanced-rule"); assertFalse(designer.design(flow).editable());
    }

    @Test void rejectsMutationsOnRunningQueuedOrServiceBackedGraphs()
    {
        DesignNode node = create("attributes"); ObjectNode p = (ObjectNode) nodes.get(node.id()).path("component");
        p.put("state", "RUNNING"); assertThrows(ServiceException.class, () -> create("capture")); p.put("state", "STOPPED");
        ((ObjectNode) nodes.get(node.id()).path("status").path("aggregateSnapshot")).put("activeThreadCount", 1);
        assertThrows(ServiceException.class, () -> create("capture"));
        ((ObjectNode) nodes.get(node.id()).path("status").path("aggregateSnapshot")).put("activeThreadCount", 0);
        services.add(mapper.valueToTree(map("id", UUID.randomUUID().toString()))); assertThrows(ServiceException.class, () -> create("capture")); services.clear();
        JsonNode edge = edge(node.id(), node.id(), "success", 1); edges.put(edge.path("component").path("id").asText(), edge);
        assertThrows(ServiceException.class, () -> create("capture")); assertEquals(1, mutations);
    }

    @Test void rejectsForeignNodesConnectionsDuplicatesAndCycles()
    {
        DesignNode a = create("attributes"), b = create("attributes"), capture = create("capture");
        assertThrows(ServiceException.class, () -> designer.updateNode(flow, UUID.randomUUID().toString(), new UpdateDesignNode(0L, "bad", null, null)));
        assertThrows(ServiceException.class, () -> designer.deleteConnection(flow, UUID.randomUUID().toString(), 0L));
        assertThrows(ServiceException.class, () -> designer.createConnection(flow, new CreateDesignConnection(a.id(), b.id(), "unknown")));
        assertThrows(ServiceException.class, () -> designer.createConnection(flow, new CreateDesignConnection(capture.id(), b.id(), "success")));
        JsonNode edge = edge(a.id(), b.id(), "success", 0); edges.put(edge.path("component").path("id").asText(), edge);
        assertThrows(ServiceException.class, () -> designer.createConnection(flow, new CreateDesignConnection(a.id(), b.id(), "success")));
        assertThrows(ServiceException.class, () -> designer.createConnection(flow, new CreateDesignConnection(b.id(), a.id(), "success")));
        assertThrows(ServiceException.class, () -> designer.deleteNode(flow, a.id(), 0L)); assertEquals(3, mutations);
    }

    @Test void designEndpointsRequireReadOrEditPermissionsAndNeverLogRequestBodies()
    {
        int found = 0;
        for (var method : DataGovernanceController.class.getDeclaredMethods())
            if (List.of("nodeTypes", "design", "createNode", "updateNode", "deleteNode", "createConnection", "deleteConnection").contains(method.getName()))
            {
                found++; boolean read = method.isAnnotationPresent(GetMapping.class);
                assertEquals("@ss.hasPermi('governance:flow:" + (read ? "list" : "edit") + "')", method.getAnnotation(PreAuthorize.class).value());
                if (!read) { assertFalse(method.getAnnotation(Log.class).isSaveRequestData()); assertFalse(method.getAnnotation(Log.class).isSaveResponseData()); }
            }
        assertEquals(7, found);
    }

    private DesignNodeType type(String id) { return designer.nodeTypes().stream().filter(t -> t.id().equals(id)).findFirst().orElseThrow(); }
    private DesignNode create(String id) { var t = type(id); return designer.createNode(flow, new CreateDesignNode(t.type(), t.name(), t.role(), new DesignPosition(0, 0), null)); }
    private JsonNode edge(String from, String to, String relationship, long queued)
    {
        return mapper.valueToTree(map("revision", map("version", 0), "component", map("id", UUID.randomUUID().toString(), "parentGroupId", flow, "source", map("id", from, "type", "PROCESSOR"), "destination", map("id", to, "type", "PROCESSOR"), "selectedRelationships", List.of(relationship)), "status", map("aggregateSnapshot", map("flowFilesQueued", queued, "bytesQueued", 0))));
    }
}
