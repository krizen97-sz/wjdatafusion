package com.hm.manage.service.governance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hm.manage.service.governance.DataGovernanceModels.*;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.hm.manage.service.governance.DataGovernanceEngine.*;

class DataGovernanceSnapshotTest
{
    @TempDir Path directory;
    private final ObjectMapper mapper = new ObjectMapper();
    private final String source = "00000000-0000-0000-0000-000000000001";
    private final String middle = "00000000-0000-0000-0000-000000000002";
    private final String capture = "00000000-0000-0000-0000-000000000003";

    private JsonNode graph(String type, Map<String, Object> properties)
    {
        return mapper.valueToTree(map("processors", List.of(node(source, STANDARD + "GenerateFlowFile", "private-canvas-comment", map("Custom Text", "original-canvas-secret")),
            node(middle, type, "private-middle-comment", properties), node(capture, UPDATE, CAPTURE, map())),
            "connections", List.of(edge(source, middle), edge(middle, capture))));
    }
    private Map<String, Object> node(String id, String type, String comment, Map<String, Object> props)
    { return map("component", map("id", id, "name", "test", "type", type, "bundle", map("group", "org.apache.nifi", "artifact", "test-nar", "version", "2.11.0"),
        "config", map("comments", comment, "properties", props))); }
    private Map<String, Object> edge(String from, String to)
    { return map("component", map("source", map("id", from, "type", "PROCESSOR"), "destination", map("id", to, "type", "PROCESSOR"), "selectedRelationships", List.of("success"))); }

    @Test void freezesOnlyEffectiveSafeConfigurationAndHashSurvivesPersistence() throws Exception
    {
        var safe = new DataGovernanceSafeFlow(graph(STANDARD + "EvaluateJsonPath", map("Destination", "flowfile-attribute", "sample.value", "$.old")));
        JsonNode snapshot = safe.freeze("{\"message\":\"synthetic\"}", map("jsonPath", "$.message"));
        String text = mapper.writeValueAsString(snapshot);
        assertFalse(text.contains("original-canvas-secret")); assertFalse(text.contains("private-canvas-comment"));
        assertFalse(text.contains("private-middle-comment")); assertTrue(text.contains("$.message")); assertFalse(text.contains("$.old"));
        assertTrue(text.contains("base64Decode")); assertTrue(text.contains("2.11.0"));
        String hash = DataGovernanceSafeFlow.hash(snapshot);
        assertEquals(64, hash.length()); assertEquals(hash, DataGovernanceSafeFlow.hash(mapper.readTree(text)));
        assertNotEquals(hash, DataGovernanceSafeFlow.hash(safe.freeze("{}", map("jsonPath", "$.message"))));
    }

    @Test void validatesParameterOverridesAfterBinding()
    {
        var safe = new DataGovernanceSafeFlow(graph(STANDARD + "RouteOnAttribute", map("Routing Strategy", "Route to Property name", "accepted", "${sample.value:isEmpty():not()}")));
        assertThrows(DataGovernanceSafeFlow.UnsupportedFlow.class, () -> safe.freeze("{}", map("requiredValue", "${SERVER_SECRET}")));
    }

    @Test void rejectedGraphsNeverPersistRawUnsafeProperties()
    {
        String flowId = "00000000-0000-0000-0000-000000000004";
        DataGovernanceProperties properties = new DataGovernanceProperties(); properties.setStorageDir(directory.toString());
        var repository = new DataGovernanceFileRunRepository(properties);
        var engine = mock(DataGovernanceEngine.class); var runner = mock(DataGovernanceTestRunner.class);
        when(engine.flow(flowId)).thenReturn(new Flow(flowId, source, "test", "", flowId, "blank", "/nifi/"));
        when(engine.groupContents(flowId)).thenReturn(graph(STANDARD + "InvokeHTTP", map("Remote URL", "https://example.invalid/private-unsafe-value")));
        var service = new DataGovernanceService(engine, runner, repository);
        try
        {
            TestRun run = service.submit(flowId, new TestInput("{}", Map.of()), 1);
            assertEquals("UNSUPPORTED", run.status); assertTrue(run.cleanupConfirmed); assertNull(run.definitionHash);
            StoredRun persisted = repository.find(run.id); assertTrue(persisted.definition == null || persisted.definition.isNull());
            assertFalse(mapper.valueToTree(persisted).toString().contains("private-unsafe-value"));
            assertFalse(mapper.valueToTree(persisted).toString().contains("original-canvas-secret"));
            verifyNoInteractions(runner);
        }
        finally { service.close(); repository.close(); }
    }
}
