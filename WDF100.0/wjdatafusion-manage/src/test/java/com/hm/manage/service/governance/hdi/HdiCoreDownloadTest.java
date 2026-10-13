package com.hm.manage.service.governance.hdi;

import com.hm.common.core.domain.model.LoginUser;
import com.hm.manage.controller.DataGovernanceHdiCoreRunsController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HdiCoreDownloadTest {
    HdiCoreRuns runs; DataGovernanceHdiCoreRunsController controller;
    @BeforeEach void setup() {
        LoginUser user = new LoginUser(); user.setUserId(7L);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null));
        runs = mock(HdiCoreRuns.class); controller = new DataGovernanceHdiCoreRunsController(mock(HdiTaskStore.class), runs);
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    @Test void downloadsCompleteBytesWithAuthenticatedOwnerAndSafeChineseFilename() {
        byte[] value = "测试,original\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        when(runs.artifact(7, "run", "artifact", 0, 1048576)).thenReturn(new HdiCoreRuns.ArtifactChunk("artifact", "输出.csv", "text/csv", 0, value.length, value.length, HdiTaskStore.sha256(value), true, value));
        var response = controller.download("run", "artifact");
        assertArrayEquals(value, response.getBody()); assertEquals(value.length, response.getHeaders().getContentLength());
        assertEquals("no-store", response.getHeaders().getCacheControl()); assertTrue(response.getHeaders().getFirst("Content-Disposition").contains("filename*=UTF-8"));
        verify(runs).artifact(7, "run", "artifact", 0, 1048576);
    }
    @Test void rejectsCorruptBytesBeforePublishingAResponse() {
        byte[] value = {1,2};
        when(runs.artifact(anyLong(), anyString(), anyString(), anyLong(), anyInt())).thenReturn(new HdiCoreRuns.ArtifactChunk("artifact", "out.csv", "text/csv", 0, 2, 2, "0".repeat(64), true, value));
        assertEquals(502, assertThrows(HdiTaskFault.class, () -> controller.download("run", "artifact")).status());
    }
    @Test void rejectsChangedManifestOrMissingLaterChunkWithoutPartialSuccess() {
        byte[] all = {1,2}; String sha = HdiTaskStore.sha256(all);
        when(runs.artifact(7, "run", "artifact", 0, 1048576)).thenReturn(new HdiCoreRuns.ArtifactChunk("artifact", "out.csv", "text/csv", 0, 1, 2, sha, false, new byte[]{1}));
        when(runs.artifact(7, "run", "artifact", 1, 1048576)).thenReturn(new HdiCoreRuns.ArtifactChunk("artifact", "changed.csv", "text/csv", 1, 2, 2, sha, true, new byte[]{2}));
        assertEquals(502, assertThrows(HdiTaskFault.class, () -> controller.download("run", "artifact")).status());
    }
    @Test void foreignOwnerRejectionIsPreserved() {
        when(runs.artifact(7, "run", "artifact", 0, 1048576)).thenThrow(new HdiTaskFault(404, "不可访问"));
        assertEquals(404, assertThrows(HdiTaskFault.class, () -> controller.download("run", "artifact")).status());
    }
    @Test void platformHttpSerializerReceivesActualNativeDataInsteadOfJsonNodeBeanProperties() throws Exception {
        when(runs.status(7, "run")).thenReturn(HdiProtocol.JSON.createObjectNode().put("state", "SUCCEEDED").put("finalized", true));
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller).build();
        var response = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/governance/hdi/runs/run")).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        var body = HdiProtocol.JSON.readTree(response.getContentAsByteArray());
        assertEquals("SUCCEEDED", body.path("data").path("state").asText());
        assertTrue(body.path("data").path("finalized").asBoolean()); assertFalse(body.path("data").has("nodeType"));
    }
}
