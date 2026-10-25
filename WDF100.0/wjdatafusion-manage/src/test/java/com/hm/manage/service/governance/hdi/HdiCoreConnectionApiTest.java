package com.hm.manage.service.governance.hdi;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.hm.common.core.domain.model.LoginUser;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartHttpServletRequest;

class HdiCoreConnectionApiTest {
    HdiCoreConnections connections; HdiCoreConnectionApi api; LoginUser user;
    @BeforeEach void setup() {
        connections = mock(HdiCoreConnections.class);
        api = new HdiCoreConnectionApi(connections, mock(HdiCoreCatalog.class), mock(HdiCompilerClient.class), mock(HdiTaskStore.class));
        user = new LoginUser(); user.setUserId(7L); user.setPermissions(Set.of("governance:flow:list", "governance:flow:edit", "governance:flow:test"));
    }
    @Test void originalMultipartFormIsDecodedWithoutAcceptingAnotherOwner() throws Exception {
        var request = new MockMultipartHttpServletRequest(); request.setMethod("POST"); request.setContentType("multipart/form-data; boundary=test");
        request.addParameter("datasourceName", "原连接"); request.addParameter("dbType", "18");
        request.addParameter("parameters", "{\"Host\":\"127.0.0.1\",\"databasePassword\":\"transport-cipher\"}");
        when(connections.saveForm(eq(7L), any())).thenReturn(HdiProtocol.JSON.createObjectNode().put("id", 9));
        api.handle("/dataSource/manage/addDatasource", request, user);
        verify(connections).saveForm(eq(7L), argThat(value -> value.path("parameters").isObject()
            && value.path("parameters").path("Host").asText().equals("127.0.0.1")));
        user.setPermissions(Set.of("governance:flow:list"));
        assertEquals(403, assertThrows(HdiTaskFault.class, () -> api.handle("/dataSource/manage/addDatasource", request, user)).status());
        verifyNoMoreInteractions(connections);
    }
    @Test void duplicateMultipartValuesAreRejectedBeforePersistence() {
        var request = new MockMultipartHttpServletRequest(); request.setMethod("POST"); request.setContentType("multipart/form-data; boundary=test");
        request.addParameter("parameters", "{}", "{}");
        assertEquals(400, assertThrows(HdiTaskFault.class, () -> api.handle("/dataSource/manage/addDatasource", request, user)).status());
        verifyNoInteractions(connections);
    }
    @Test void sourceTableDiscoveryAndRegisteredTableReadRemainOwnerBound() throws Exception {
        var request = new MockHttpServletRequest("GET", "/");
        when(connections.tables(eq(7L), eq(9L), any())).thenReturn(Map.of("list", List.of(Map.of("id", 12, "dbTableName", "source_rows")), "total", 1));
        assertEquals(List.of(Map.of("id", 12, "dbTableName", "source_rows", "enable", true, "disable", false)), api.handle("/globalConfig/dataSourceTableManage/getTables/9", request, user));
        request.addParameter("tableId", "12");
        when(connections.getTable(7, 12)).thenReturn(Map.of("id", 12));
        assertEquals(Map.of("id", 12), api.handle("/globalConfig/dataSourceTableManage/getTable", request, user));
        verify(connections).getTable(7, 12);
    }
    @Test void savedConnectionTestingRequiresActualTestPermission() {
        var request = new MockHttpServletRequest("POST", "/"); request.addParameter("prop", "9");
        user.setPermissions(Set.of("governance:flow:list"));
        assertEquals(403, assertThrows(HdiTaskFault.class, () -> api.handle("/dataSource/manage/testById", request, user)).status());
        verifyNoInteractions(connections);
    }
    @Test void nodeSelectionUsesStringTypeWithoutMutatingThePersistedConnectionDto() throws Exception {
        var source = HdiProtocol.JSON.createObjectNode().put("id", 9).put("dbType", 8).put("_platformRevision", "1");
        when(connections.get(7, 9)).thenReturn(source);
        var request = new MockHttpServletRequest("POST", "/");
        JsonNode result = (JsonNode)api.handle("/globalConfig/dataSourceManage/getDatasourceById/9", request, user);
        assertTrue(result.path("dbType").isTextual()); assertEquals("8", result.path("dbType").asText());
        assertTrue(source.path("dbType").isIntegralNumber()); verify(connections).get(7, 9);
    }
}
