package com.hm.manage.service.governance.hdi;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HdiOriginalMetadataGatewayTest {
    static class Client extends HdiCompilerClient {
        JsonNode value,parameters;
        Client(JsonNode value){super(new HdiCoreProperties());this.value=value;}
        @Override public JsonNode connectionDescription(String operation,String type,String version,JsonNode parameters){assertEquals("connectionMetadata",operation);this.parameters=parameters;return value;}
    }
    ObjectNode source(){return HdiProtocol.JSON.createObjectNode().put("dbType",2).put("databasePassword","private-test-secret");}
    ObjectNode binding(){var b=HdiProtocol.JSON.createObjectNode().put("kind","mysql");b.putArray("endpoints").addObject().put("host","127.0.0.1").put("port",13306);return b;}
    @Test void metadataRunsInChildAndGuardIsMandatory(){
        var response=HdiProtocol.JSON.createObjectNode();response.putObject("metadata").put("available",true).put("guardActive",true);
        Client client=new Client(response);SecurityManager before=System.getSecurityManager();new HdiOriginalMetadataGateway(client).read("MySQL",source(),binding(),"test",null);
        assertEquals("127.0.0.1:13306",client.parameters.path("allowedEndpoints").get(0).asText());assertSame(before,System.getSecurityManager());
        ((ObjectNode)response.path("metadata")).put("guardActive",false);assertEquals(502,assertThrows(HdiTaskFault.class,()->new HdiOriginalMetadataGateway(client).read("MySQL",source(),binding(),"test",null)).status());
    }
    @Test void errorMessagesDoNotEchoRawClientDiagnostics(){
        var response=HdiProtocol.JSON.createObjectNode();response.putObject("metadata").put("available",false).put("reason","password=private-test-secret");
        var failure=assertThrows(HdiTaskFault.class,()->new HdiOriginalMetadataGateway(new Client(response)).read("MySQL",source(),binding(),"test",null));
        assertFalse(failure.getMessage().contains("private-test-secret"));assertEquals(422,failure.status());
    }
}
