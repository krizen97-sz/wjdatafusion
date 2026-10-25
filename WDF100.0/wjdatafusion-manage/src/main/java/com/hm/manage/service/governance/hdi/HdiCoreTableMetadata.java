package com.hm.manage.service.governance.hdi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Original node metadata HTTP projections over actual, owner-scoped tables and topics. Never creates a business table. */
@Service
public class HdiCoreTableMetadata {
    private final HdiCoreConnections connections;
    public HdiCoreTableMetadata(HdiCoreConnections connections) { this.connections = connections; }
    public boolean handles(String route) { return Set.of("getTaskTables", "getTaskTable", "checkTargetTable").contains(route); }
    public Object handle(String route, long owner, JsonNode request) {
        if (request == null || !request.isObject()) throw bad("表结构请求必须是对象");
        return switch (route) {
            case "getTaskTables" -> taskTables(owner, request);
            case "getTaskTable" -> taskTable(owner, request);
            case "checkTargetTable" -> checkTargetTable(owner, request);
            default -> throw new HdiTaskFault(422, "此表结构操作尚未接入平台");
        };
    }
    public List<Map<String,Object>> taskTables(long owner, JsonNode request) {
        long connectionId = connectionId(request); JsonNode source = connections.get(owner, connectionId);
        ObjectNode query = ((ObjectNode)request).deepCopy(); query.put("pageSize", 200);
        String schema = query.path("schemaCode").asText("");
        if (schema.isBlank()) schema = query.path("tableSchem").asText(query.path("tableSchema").asText(""));
        if (source.path("dbType").asInt() == 16 && schema.isBlank()) {
            // The original output cascader groups this unpaged result by schema. An omitted schema
            // means all visible schemas; it is not the paged queryTablePage default of public.
            List<Map<String,Object>> all = new ArrayList<>(); Set<String> schemas = new LinkedHashSet<>();
            for (Map<String,Object> item : connections.schemas(owner, connectionId)) {
                String actual = text(item.get("schemaCode"), "数据库模式");
                if (!schemas.add(actual)) continue;
                all.addAll(taskTables(owner, query.deepCopy().put("schemaCode", actual)));
                if (all.size() > 1000) throw new HdiTaskFault(422, "所有模式的表数量超过当前浏览范围");
            }
            return all;
        }
        if (!schema.isBlank()) query.put("schemaCode", schema);
        List<Map<String,Object>> rows = new ArrayList<>();
        for (int page = 1; page <= 5; page++) {
            query.put("pageNo", page); Map<String,Object> response = connections.tables(owner, connectionId, query);
            List<?> batch = list(response.get("list")); int total = number(response.get("total"));
            if (total > 1000 || rows.size() + batch.size() > 1000) throw new HdiTaskFault(422, "表数量超过当前浏览范围");
            for (Object item : batch) {
                Map<String,Object> row = copy(item); row.put("tableId", row.get("id")); row.put("tableInfoId", row.get("id"));
                // The original TaskTableVo exposes both names; they describe the same discovered object.
                row.putIfAbsent("dbTableName", row.get("tableName")); row.putIfAbsent("tableSchem", row.get("tableSchema"));
                rows.add(row);
            }
            if (rows.size() >= total) return rows;
            if (batch.isEmpty()) throw new HdiTaskFault(422, "表列表发生变化，请重新查询");
        }
        throw new HdiTaskFault(422, "表数量超过当前浏览范围");
    }
    public Map<String,Object> taskTable(long owner, JsonNode request) {
        long connectionId = connectionId(request); JsonNode source = connections.get(owner, connectionId);
        Map<String,Object> found = findActualTable(owner, connectionId, source.path("dbType").asInt(), request);
        long registeredId = id(found.get("id"), "表标识");
        JsonNode requestedId = request.hasNonNull("id") ? request.get("id") : request.get("tableId");
        if (requestedId != null && !requestedId.asText().isBlank() && !requestedId.asText().equals("0")
                && HdiProtocol.positive(requestedId.asText(), "表标识") != registeredId) throw bad("表标识与所选连接、表名不一致");
        Map<String,Object> result = new LinkedHashMap<>(connections.getTable(owner, registeredId));
        if (id(result.get("dbId"), "连接标识") != connectionId) throw bad("表不属于所选连接");
        result.put("tableId", registeredId); result.put("fields", result.getOrDefault("fieldUpdateRequestList", List.of()));
        result.putIfAbsent("tableSchem", result.get("tableSchema")); return result;
    }
    public Map<String,Object> checkTargetTable(long owner, JsonNode request) {
        long connectionId = connectionId(request); JsonNode source = connections.get(owner, connectionId);
        JsonNode target = request.has("databaseTable") ? request.get("databaseTable") : request;
        if (!target.isObject()) throw bad("请选择目标表"); ObjectNode tableRequest = ((ObjectNode)target).deepCopy(); tableRequest.put("dbId", connectionId);
        Map<String,Object> actual = taskTable(owner, tableRequest);
        Map<String,Object> result = new LinkedHashMap<>(); result.put("tableId", actual.get("id")); result.put("targetTableName", actual.get("tableName"));
        result.put("params", List.of()); result.put("columnList", List.of());
        JsonNode requested = request.has("dstTableFieldVoList") ? request.get("dstTableFieldVoList") : request.path("fieldList");
        if (!requested.isMissingNode() && !requested.isNull() && !requested.isArray() || requested.size() > 512) throw bad("目标字段列表无效");
        // The original service deliberately skips relational-column comparison for Kafka. Existence has
        // already been verified by actual listTopics; do not invent a Kafka message schema.
        if (source.path("dbType").asInt() == 8) {
            result.put("access", true); result.put("columns", List.of()); result.put("schemaChecked", false); return result;
        }
        if (source.path("dbType").asInt() != 16) throw new HdiTaskFault(422, "此连接不提供目标关系表核对");
        LinkedHashMap<String,Map<String,Object>> remaining = new LinkedHashMap<>();
        for (Object item : list(actual.get("fieldUpdateRequestList"))) {
            Map<String,Object> field = copy(item); String name = text(field.get("columnName"), "目标列名");
            Map<String,Object> column = new LinkedHashMap<>();
            column.put("columnName", name); column.put("typeName", field.get("columnTypeName")); column.put("dataType", field.get("columnDataType"));
            column.put("columnSize", field.get("columnSize")); column.put("decimalDigits", field.get("decimalDigits"));
            column.put("isNullable", field.get("columnNullable")); column.put("isAutoincrement", field.get("columnAutoincrement")); column.put("remarks", field.get("columnRemarks"));
            column.put("columnFamily", field.getOrDefault("columnFamily", ""));
            // The packaged comparison dialog consumes these aliases; all values come from the actual metadata.
            column.put("fieldName", name); column.put("fieldType", field.get("columnTypeName")); column.put("fieldSize", field.get("columnSize"));
            if (remaining.put(name, column) != null) throw new HdiTaskFault(422, "目标表包含重复列名");
        }
        List<Map<String,Object>> aligned = new ArrayList<>(); boolean access = true;
        for (JsonNode field : requested) {
            if (!field.isObject()) throw bad("目标字段格式无效"); String name = text(field.path("fieldName").asText(), "输出字段名称");
            Map<String,Object> column = remaining.remove(name);
            if (column == null) { access = false; aligned.add(Map.of("columnName", "", "typeName", "", "fieldName", "", "fieldType", "")); }
            else aligned.add(column);
        }
        // Original DataSourceTableManageServiceImpl checks exact column-name existence. It does not
        // guess coercions from fieldType or claim that a data write has already succeeded.
        if (!access) remaining.values().stream().sorted(Comparator.comparing(value -> value.get("columnName").toString())).forEach(aligned::add);
        result.put("access", access); result.put("columns", access ? List.of() : aligned); result.put("schemaChecked", true); return result;
    }
    private Map<String,Object> findActualTable(long owner, long connectionId, int type, JsonNode request) {
        String name = text(request.path("tableName").asText(request.path("dbTableName").asText()), "表名称");
        String schema = request.path("tableSchem").asText(request.path("tableSchema").asText(request.path("schemaCode").asText("")));
        if (type == 16 && schema.isBlank()) schema = "public";
        ObjectNode query = HdiProtocol.JSON.createObjectNode().put("dbId", connectionId).put("tableName", name).put("schemaCode", schema);
        for (Map<String,Object> row : taskTables(owner, query)) {
            if (!name.equals(row.get("tableName"))) continue;
            if (type == 16 && !schema.equals(row.get("tableSchema"))) continue;
            return row;
        }
        throw new HdiTaskFault(404, "目标表或主题不存在，未创建或修改业务对象");
    }
    private static long connectionId(JsonNode request) {
        JsonNode value = request.hasNonNull("etlDatasourceId") ? request.get("etlDatasourceId") : request.get("dbId");
        if (value == null) throw bad("请选择连接"); return HdiProtocol.positive(value.asText(), "连接标识");
    }
    private static long id(Object value, String name) { return HdiProtocol.positive(String.valueOf(value), name); }
    private static String text(Object value, String name) { String text = value == null ? "" : value.toString(); if (text.isBlank() || text.length() > 256 || text.indexOf('\0') >= 0) throw bad(name + "无效"); return text; }
    private static int number(Object value) { if (!(value instanceof Number n) || n.longValue() < 0 || n.longValue() > Integer.MAX_VALUE) throw bad("表列表总数无效"); return ((Number)value).intValue(); }
    private static List<?> list(Object value) { if (!(value instanceof List<?> values)) throw bad("表结构响应无效"); return values; }
    private static Map<String,Object> copy(Object value) {
        if (!(value instanceof Map<?,?> map)) throw bad("表结构响应无效"); Map<String,Object> result = new LinkedHashMap<>();
        for (Map.Entry<?,?> entry : map.entrySet()) { if (!(entry.getKey() instanceof String)) throw bad("表结构字段无效"); result.put((String)entry.getKey(), entry.getValue()); } return result;
    }
    private static HdiTaskFault bad(String message) { return new HdiTaskFault(400, message); }
}
