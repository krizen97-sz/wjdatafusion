package com.hm.manage.service.governance.hdi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HdiCoreTableMetadataTest {
    HdiCoreConnections connections; HdiCoreTableMetadata metadata;
    @TempDir Path directory;
    @BeforeEach void initialize() {
        connections = mock(HdiCoreConnections.class); metadata = new HdiCoreTableMetadata(connections);
        when(connections.get(7, 1)).thenReturn(HdiProtocol.JSON.createObjectNode().put("id", 1).put("dbType", 16));
        when(connections.tables(eq(7L), eq(1L), any())).thenAnswer(call -> {
            JsonNode query = call.getArgument(2); List<Map<String,Object>> rows = new ArrayList<>();
            if ("source_rows".contains(query.path("tableName").asText(""))) rows.add(Map.of("id", 11L, "dbId", 1L, "tableName", "source_rows", "tableSchema", "fixture"));
            return Map.of("list", rows, "total", rows.size());
        });
        when(connections.getTable(7, 11)).thenReturn(Map.of("id", 11L, "dbId", 1L, "tableName", "source_rows", "tableSchema", "fixture", "fieldUpdateRequestList", List.of(
            Map.of("columnName", "id", "columnTypeName", "int4", "columnDataType", "4", "columnSize", 10),
            Map.of("columnName", "message", "columnTypeName", "varchar", "columnDataType", "12", "columnSize", 200))));
    }
    ObjectNode tableRequest() { return HdiProtocol.JSON.createObjectNode().put("dbId", 1).put("tableName", "source_rows").put("tableSchem", "fixture"); }
    ObjectNode targetRequest(String... names) {
        ObjectNode body = HdiProtocol.JSON.createObjectNode().put("etlDatasourceId", 1); body.set("databaseTable", tableRequest()); var fields = body.putArray("dstTableFieldVoList");
        for (String name : names) fields.addObject().put("fieldName", name).put("fieldType", "original-dialog-value"); return body;
    }
    @Test void originalGetTaskTableReturnsTheActualRegisteredIdentityAndFields() {
        var value = metadata.taskTable(7, tableRequest()); assertEquals(11L, value.get("id")); assertEquals(value.get("fields"), value.get("fieldUpdateRequestList"));
        assertEquals(11L, metadata.taskTable(7, tableRequest().put("id", 11)).get("tableId"));
        assertEquals(400, assertThrows(HdiTaskFault.class, () -> metadata.taskTable(7, tableRequest().put("id", 12))).status());
        assertEquals(404, assertThrows(HdiTaskFault.class, () -> metadata.taskTable(7, tableRequest().put("tableName", "missing"))).status());
        verify(connections, never()).saveForm(anyLong(), any()); verify(connections, never()).delete(anyLong(), anyLong(), anyLong());
    }
    @Test void targetComparisonUsesRealCaseSensitiveColumnNamesAndPreservesOriginalMismatchLayout() {
        var pass = metadata.checkTargetTable(7, targetRequest("message", "id")); assertEquals(true, pass.get("access")); assertEquals(11L, pass.get("tableId"));
        var fail = metadata.checkTargetTable(7, targetRequest("MESSAGE", "id")); assertEquals(false, fail.get("access"));
        List<?> fields = (List<?>)fail.get("columns"); assertEquals(3, fields.size());
        assertEquals("", ((Map<?,?>)fields.get(0)).get("fieldName")); assertEquals("id", ((Map<?,?>)fields.get(1)).get("fieldName")); assertEquals("message", ((Map<?,?>)fields.get(2)).get("fieldName"));
        assertEquals("varchar", ((Map<?,?>)fields.get(2)).get("fieldType")); assertEquals(200, ((Map<?,?>)fields.get(2)).get("fieldSize"));
        assertEquals(false, metadata.checkTargetTable(7, targetRequest("id", "id")).get("access"));
    }
    @Test void missingTargetsAndForeignOwnersAreNeverReportedAsAccessible() {
        assertEquals(404, assertThrows(HdiTaskFault.class, () -> metadata.checkTargetTable(7, targetRequest("id").set("databaseTable", tableRequest().put("tableName", "missing")))).status());
        when(connections.get(8, 1)).thenThrow(new HdiTaskFault(404, "连接不存在或无权访问"));
        assertEquals(404, assertThrows(HdiTaskFault.class, () -> metadata.handle("getTaskTables", 8, tableRequest())).status());
    }
    @Test void originalListContractCollectsActualPagesInsteadOfReturningOnlyTheFirstPage() {
        when(connections.tables(eq(7L), eq(1L), any())).thenAnswer(call -> {
            JsonNode query = call.getArgument(2); int page = query.path("pageNo").asInt();
            assertEquals(200, query.path("pageSize").asInt());
            return Map.of("total", 2, "list", List.of(Map.of("id", (long)page, "dbId", 1L, "tableName", "table" + page, "tableSchema", "fixture")));
        });
        var all = metadata.taskTables(7, tableRequest()); assertEquals(2, all.size()); assertEquals(2L, all.get(1).get("tableId"));
    }
    @Test void omittedSchemaEnumeratesActualOwnedSchemasAndKeepsTheirRegisteredTableIds() {
        when(connections.schemas(7, 1)).thenReturn(List.of(Map.of("schemaCode", "public"), Map.of("schemaCode", "fixture")));
        when(connections.tables(eq(7L), eq(1L), any())).thenAnswer(call -> {
            JsonNode query = call.getArgument(2); String schema = query.path("schemaCode").asText();
            long id = schema.equals("public") ? 10L : 11L;
            return Map.of("total", 1, "list", List.of(Map.of("id", id, "dbId", 1L, "tableName", "table_" + id, "tableSchema", schema)));
        });
        var all = metadata.taskTables(7, HdiProtocol.JSON.createObjectNode().put("dbId", 1));
        assertEquals(2, all.size()); assertEquals(10L, all.get(0).get("id")); assertEquals("fixture", all.get(1).get("tableSchem")); assertEquals(11L, all.get(1).get("id"));
        clearInvocations(connections);
        assertEquals(1, metadata.taskTables(7, tableRequest()).size()); verify(connections, never()).schemas(anyLong(), anyLong());
    }
    @Test void allSchemaAggregationKeepsTheSameTotalBound() {
        when(connections.schemas(7, 1)).thenReturn(List.of(Map.of("schemaCode", "one"), Map.of("schemaCode", "two")));
        when(connections.tables(eq(7L), eq(1L), any())).thenAnswer(call -> {
            JsonNode query = call.getArgument(2); List<Map<String,Object>> rows = new ArrayList<>();
            for (int index = 0; index < 200 && (query.path("pageNo").asInt() - 1) * 200 + index < 501; index++)
                rows.add(Map.of("id", (long)index + 1, "dbId", 1L, "tableName", "table" + index, "tableSchema", query.path("schemaCode").asText()));
            return Map.of("list", rows, "total", 501);
        });
        assertEquals(422, assertThrows(HdiTaskFault.class, () -> metadata.taskTables(7, HdiProtocol.JSON.createObjectNode().put("dbId", 1))).status());
    }
    @Test void kafkaRequiresAnActuallyListedTopicAndDoesNotInventAColumnSchema() {
        when(connections.get(7, 1)).thenReturn(HdiProtocol.JSON.createObjectNode().put("id", 1).put("dbType", 8));
        when(connections.getTable(7, 11)).thenReturn(Map.of("id", 11L, "dbId", 1L, "tableName", "source_rows", "tableSchema", "", "fieldUpdateRequestList", List.of()));
        var value = metadata.checkTargetTable(7, targetRequest("body")); assertEquals(true, value.get("access")); assertEquals(false, value.get("schemaChecked")); assertEquals(List.of(), value.get("columns"));
        assertEquals(404, assertThrows(HdiTaskFault.class, () -> metadata.taskTable(7, tableRequest().put("tableName", "no_topic"))).status());
        verify(connections, never()).schemas(anyLong(), anyLong());
    }
    @Test @EnabledIfEnvironmentVariable(named = "HDI_CONNECTION_FIXTURE_DIR", matches = ".+")
    void existingPostgresTableIsReadAndComparedWithoutCreatingBusinessObjects() throws Exception {
        HdiCoreConnectionsTest fixture = new HdiCoreConnectionsTest(); fixture.privateRoot = directory; fixture.initialize();
        JsonNode secret = HdiProtocol.JSON.readTree(Files.readAllBytes(Path.of(System.getenv("HDI_CONNECTION_FIXTURE_DIR")).resolve("postgres-connection.json")));
        var form = fixture.form("PostgreSQL", "目标表元数据验收", secret.path("password").asText()); var parameters = (ObjectNode)form.path("parameters");
        parameters.put("databaseIp", secret.path("host").asText()).put("databasePort", secret.path("port").asText()).put("databaseSid", secret.path("database").asText()).put("databaseUsername", secret.path("username").asText());
        long id = fixture.connections.saveForm(7, form).path("id").asLong(); HdiCoreTableMetadata live = new HdiCoreTableMetadata(fixture.connections);
        var request = HdiProtocol.JSON.createObjectNode().put("dbId", id).put("tableName", "source_rows").put("tableSchem", secret.path("schema").asText());
        var before = fixture.connections.tables(7, id, HdiProtocol.JSON.createObjectNode().put("schemaCode", secret.path("schema").asText()));
        var allSchemas = live.taskTables(7, HdiProtocol.JSON.createObjectNode().put("dbId", id));
        assertTrue(allSchemas.stream().anyMatch(row -> "output_verified_rows".equals(row.get("dbTableName")) && secret.path("schema").asText().equals(row.get("tableSchem")) && ((Number)row.get("id")).longValue() > 0));
        var table = live.taskTable(7, request); assertEquals(table.get("id"), live.taskTable(7, request).get("id"));
        ObjectNode check = HdiProtocol.JSON.createObjectNode().put("etlDatasourceId", id); check.set("databaseTable", request); var fields = check.putArray("dstTableFieldVoList");
        for (Object field : (List<?>)table.get("fields")) fields.addObject().put("fieldName", ((Map<?,?>)field).get("columnName").toString());
        assertEquals(true, live.checkTargetTable(7, check).get("access")); fields.addObject().put("fieldName", "definitely_missing_column"); assertEquals(false, live.checkTargetTable(7, check).get("access"));
        assertEquals(404, assertThrows(HdiTaskFault.class, () -> live.taskTable(7, request.deepCopy().put("tableName", "must_not_be_created"))).status());
        var after = fixture.connections.tables(7, id, HdiProtocol.JSON.createObjectNode().put("schemaCode", secret.path("schema").asText()));
        assertEquals(before.get("total"), after.get("total"));
    }
}
