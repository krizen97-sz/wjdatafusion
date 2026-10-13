package com.hm.manage.service.governance.hdi;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.junit.jupiter.api.Assertions.*;

class HdiProtocolTest {
    String originalEncoding(String json) { return Base64.getEncoder().encodeToString(URLEncoder.encode(json, StandardCharsets.UTF_8).replace("+", "%20").getBytes(StandardCharsets.UTF_8)); }
    @Test void originalEncodingPreservesChineseSymbolsAndLiteralHtmlWithoutChangingTaskData() {
        var input = HdiProtocol.JSON.createObjectNode().put("visualBase64", originalEncoding("{\"flowVo\":{\"flowDisplayName\":\"流程 + <样本>\"},\"nodes\":{}}"));
        assertEquals("流程 + <样本>", HdiProtocol.visual(input, 10000).path("flowVo").path("flowDisplayName").asText());
    }
    @Test void malformedEncodingDuplicateKeysAndTypeMetadataCannotReachOriginalSdk() throws Exception {
        for (String value : new String[]{"{\"flowVo\":{},\"flowVo\":{}}", "{\"flowVo\":{},\"nodes\":{\"a\":{\"@type\":\"arbitrary.Type\"}}}"}) {
            assertThrows(HdiTaskFault.class, () -> HdiProtocol.visual(HdiProtocol.JSON.createObjectNode().put("visualBase64", originalEncoding(value)), 10000));
        }
        var invalidUtf = HdiProtocol.JSON.createObjectNode().put("visualBase64", Base64.getEncoder().encodeToString("%7B%22flowVo%22:%7B%22name%22:%22%E0%22%7D%7D".getBytes(StandardCharsets.UTF_8)));
        assertThrows(HdiTaskFault.class, () -> HdiProtocol.visual(invalidUtf, 10000));
        var duplicate = new MockHttpServletRequest(); duplicate.setContent("{\"a\":1,\"a\":2}".getBytes(StandardCharsets.UTF_8));
        assertThrows(HdiTaskFault.class, () -> HdiProtocol.body(duplicate, 1000));
    }
    @Test void repliesUseNativeEnvelopeWhileFailureKeepsRealHttpStatus() throws Exception {
        var response = new MockHttpServletResponse(); HdiProtocol.send(response, "17");
        var body = HdiProtocol.JSON.readTree(response.getContentAsByteArray()); assertEquals("0", body.path("code").asText()); assertEquals("17", body.path("data").asText());
        var error = new MockHttpServletResponse(); HdiProtocol.failure(error, new HdiTaskFault(409, "修订已变化"));
        assertEquals(409, error.getStatus()); assertFalse(HdiProtocol.JSON.readTree(error.getContentAsByteArray()).path("success").asBoolean());
    }
}
