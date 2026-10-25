package com.hm.manage.service.governance.hdi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HdiKafkaMetadataGatewayTest {
    private static final class Client extends HdiCompilerClient {
        JsonNode response, parameters;
        Client(JsonNode response) { super(new HdiCoreProperties()); this.response = response; }
        @Override public JsonNode connectionDescription(String operation, String type, String version, JsonNode parameters) {
            assertEquals("connectionMetadata", operation); assertEquals("Kafka", type); assertNull(version);
            this.parameters = parameters; return response;
        }
    }
    ObjectNode binding() {
        var result = HdiProtocol.JSON.createObjectNode().put("kind", "kafka");
        result.putArray("endpoints").addObject().put("host", "127.0.0.1").put("port", 19092);
        return result;
    }
    ObjectNode response() {
        var result = HdiProtocol.JSON.createObjectNode(); var metadata = result.putObject("metadata").put("available", true).put("guardActive", true);
        metadata.putArray("nodes").addObject().put("host", "127.0.0.1").put("port", 19092).put("id", 1);
        metadata.putArray("topics").add("owned-topic"); return result;
    }
    @Test void sendsOnlyPinnedMetadataParametersAndDoesNotInstallAPlatformGuard() {
        SecurityManager before = System.getSecurityManager(); Client client = new Client(response());
        JsonNode result = new HdiKafkaMetadataGateway(client).read("127.0.0.1:19092", binding(), true);
        assertEquals("owned-topic", result.path("topics").get(0).asText());
        assertEquals("topics", client.parameters.path("action").asText());
        assertEquals("127.0.0.1:19092", client.parameters.path("allowedEndpoints").get(0).asText());
        assertEquals(3, client.parameters.size()); assertSame(before, System.getSecurityManager());
    }
    @Test void missingGuardOrUnexpectedReturnedEndpointCannotBecomeAConnectionSuccess() {
        ObjectNode source = response(); ((ObjectNode)source.path("metadata")).put("guardActive", false);
        assertEquals(502, assertThrows(HdiTaskFault.class, () -> new HdiKafkaMetadataGateway(new Client(source)).read("127.0.0.1:19092", binding(), false)).status());
        ((ObjectNode)source.path("metadata")).put("guardActive", true);
        ((ObjectNode)source.path("metadata").path("nodes").get(0)).put("port", 80);
        assertEquals(502, assertThrows(HdiTaskFault.class, () -> new HdiKafkaMetadataGateway(new Client(source)).read("127.0.0.1:19092", binding(), false)).status());
    }
    @Test void failuresUseFixedPlatformMessagesWithoutEchoingChildDiagnostics() {
        var source = HdiProtocol.JSON.createObjectNode(); var metadata = source.putObject("metadata").put("available", false).put("reason", "METADATA_ENDPOINT_DENIED");
        HdiTaskFault denied = assertThrows(HdiTaskFault.class, () -> new HdiKafkaMetadataGateway(new Client(source)).read("127.0.0.1:19092", binding(), false));
        assertEquals(422, denied.status()); assertTrue(denied.getMessage().contains("连接前阻止"));
        metadata.put("reason", "password=private-value");
        assertFalse(assertThrows(HdiTaskFault.class, () -> new HdiKafkaMetadataGateway(new Client(source)).read("127.0.0.1:19092", binding(), false)).getMessage().contains("private-value"));
    }
}
