package com.hm.manage.service.governance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hm.common.exception.ServiceException;
import com.hm.manage.service.governance.DataGovernanceModels.TestInput;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static com.hm.manage.service.governance.DataGovernanceEngine.*;

class DataGovernanceSafetyTest
{
    private final ObjectMapper mapper = new ObjectMapper();
    private ObjectNode blueprint(String middleType, Map<String, Object> properties)
    {
        String source = "00000000-0000-0000-0000-000000000001", mid = "00000000-0000-0000-0000-000000000002", sink = "00000000-0000-0000-0000-000000000003";
        return mapper.valueToTree(map("processors", java.util.List.of(
            map("component", map("id", source, "type", STANDARD + "GenerateFlowFile", "config", map("properties", map()))),
            map("component", map("id", mid, "type", middleType, "config", map("properties", properties))),
            map("component", map("id", sink, "type", UPDATE, "comments", CAPTURE, "config", map("properties", map())))),
            "connections", java.util.List.of(
                map("component", map("source", map("id", source, "type", "PROCESSOR"), "destination", map("id", mid, "type", "PROCESSOR"), "selectedRelationships", java.util.List.of("success"))),
                map("component", map("source", map("id", mid, "type", "PROCESSOR"), "destination", map("id", sink, "type", "PROCESSOR"), "selectedRelationships", java.util.List.of("success"))))));
    }
    @Test void rejectsActualCanvasExternalNodesAndEnvironmentReads()
    {
        assertThrows(DataGovernanceSafeFlow.UnsupportedFlow.class, () -> new DataGovernanceSafeFlow(blueprint(STANDARD + "InvokeHTTP", map("Remote URL", "https://example.invalid"))));
        assertThrows(DataGovernanceSafeFlow.UnsupportedFlow.class, () -> new DataGovernanceSafeFlow(blueprint(UPDATE, map("sample.value", "${DATABASE_PASSWORD}"))));
        assertThrows(DataGovernanceSafeFlow.UnsupportedFlow.class, () -> new DataGovernanceSafeFlow(blueprint(JOLT, map("Custom Transformation Class Name", "custom.ExternalCode"))));
        assertThrows(DataGovernanceSafeFlow.UnsupportedFlow.class, () -> new DataGovernanceSafeFlow(blueprint(JOLT, map("Jolt Transform", "jolt-transform-chain", "Jolt Specification", "[{\"operation\":\"CustomCode\",\"spec\":{}}]"))));
    }
    @Test void usesRealJoltTypeAndApiDefaultsAndRejectsFileOrAdvancedSpecs()
    {
        String spec = "[{\"operation\":\"shift\",\"spec\":{\"message\":\"renamed\"}},{\"operation\":\"default\",\"spec\":{\"origin\":\"nifi\"}}]";
        ObjectNode safe = blueprint("org.apache.nifi.processors.jolt.JoltTransformJSON", map("Jolt Transform", "jolt-transform-chain",
            "Jolt Specification", spec, "JSON Source", "FLOW_FILE", "Retain Unicode Escape Sequences", "false", "Transform Cache Size", "1", "Max String Length", "20 MB", "Pretty Print", "false"));
        assertEquals(3, new DataGovernanceSafeFlow(safe).order.size());
        assertThrows(DataGovernanceSafeFlow.UnsupportedFlow.class, () -> new DataGovernanceSafeFlow(blueprint(STANDARD + "JoltTransformJSON", map())));
        assertThrows(DataGovernanceSafeFlow.UnsupportedFlow.class, () -> new DataGovernanceSafeFlow(blueprint(JOLT, map("Jolt Specification", "/private/spec.json"))));
        assertThrows(DataGovernanceSafeFlow.UnsupportedFlow.class, () -> new DataGovernanceSafeFlow(blueprint(JOLT, map("Jolt Transform", "jolt-transform-modify-overwrite-beta", "Jolt Specification", "{}"))));
        assertThrows(DataGovernanceSafeFlow.UnsupportedFlow.class, () -> new DataGovernanceSafeFlow(blueprint(JOLT, map("JSON Source", "ATTRIBUTE", "Jolt Specification", spec))));
    }
    @Test void acceptsEngineDefaultsButRejectsStatefulAttributeMutation()
    {
        ObjectNode flow = blueprint(UPDATE, map("Store State", "Do not store state", "Cache Value Lookup Cache Size", "100", "sample.value", "fixed"));
        assertEquals(3, new DataGovernanceSafeFlow(flow).order.size());
        assertThrows(DataGovernanceSafeFlow.UnsupportedFlow.class, () -> new DataGovernanceSafeFlow(blueprint(UPDATE, map("Store State", "Store state locally", "sample.value", "fixed"))));
    }
    @Test void acceptsFiniteSamplePathAndRejectsCyclesAndGroups()
    {
        ObjectNode safe = blueprint(STANDARD + "EvaluateJsonPath", map("Destination", "flowfile-attribute", "sample.value", "$.message"));
        assertEquals(3, new DataGovernanceSafeFlow(safe).order.size());
        safe.putArray("processGroups").addObject().put("id", "untrusted");
        assertThrows(DataGovernanceSafeFlow.UnsupportedFlow.class, () -> new DataGovernanceSafeFlow(safe));
    }
    @Test void rejectsUnboundedInvalidAndInjectedTestParameters()
    {
        var service = new DataGovernanceService(mock(DataGovernanceEngine.class), mock(DataGovernanceTestRunner.class), mock(DataGovernanceRunRepository.class));
        try
        {
            service.validateInput(new TestInput("{\"message\":\"${SERVER_SECRET}\"}", Map.of())); // content remains inert; the runner Base64-wraps it.
            assertThrows(ServiceException.class, () -> service.validateInput(new TestInput("x".repeat(262145), Map.of())));
            assertThrows(ServiceException.class, () -> service.validateInput(new TestInput("[" + "{},".repeat(100) + "{}]", Map.of())));
            assertThrows(ServiceException.class, () -> service.validateInput(new TestInput("{bad}", Map.of())));
            assertThrows(ServiceException.class, () -> service.validateInput(new TestInput("{}", Map.of("baseUrl", "http://example.invalid"))));
        }
        finally { service.close(); }
    }
}
