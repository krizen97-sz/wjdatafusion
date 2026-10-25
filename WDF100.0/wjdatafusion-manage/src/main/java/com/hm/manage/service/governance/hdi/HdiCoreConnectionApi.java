package com.hm.manage.service.governance.hdi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hm.common.core.domain.model.LoginUser;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** HTTP contracts consumed by the packaged source list, connection forms and node selectors. */
@Component
@ConditionalOnProperty(prefix = "data-governance-hdi-core", name = "enabled", havingValue = "true")
public class HdiCoreConnectionApi {
    private static final String NEW = "/dataSource/manage/", OLD = "/globalConfig/dataSourceManage/", TABLE = "/globalConfig/dataSourceTableManage/";
    private final HdiCoreConnections connections;
    private final HdiCoreCatalog catalog;
    private final HdiCompilerClient compiler;
    private final HdiTaskStore store;
    private HdiCoreTableMetadata tableMetadata;
    @org.springframework.beans.factory.annotation.Autowired
    public void setTableMetadata(HdiCoreTableMetadata value) { this.tableMetadata = value; }
    private final Map<String, JsonNode> descriptors = new ConcurrentHashMap<>();
    public HdiCoreConnectionApi(HdiCoreConnections connections, HdiCoreCatalog catalog, HdiCompilerClient compiler, HdiTaskStore store) {
        this.connections = connections; this.catalog = catalog; this.compiler = compiler; this.store = store;
    }
    public boolean handles(String path) {
        return path.startsWith(NEW) || path.startsWith(TABLE) || path.equals("/_platform/connections/usage")
            || path.equals(OLD + "getDatasourceOptions") || path.startsWith(OLD + "getExtInfos/")
            || path.startsWith(OLD + "getDatasourceById/")
            || path.startsWith(OLD + "getTables/") || path.startsWith(OLD + "getDatabaseColumns/")
            || path.equals(OLD + "listMetadataSchema") || path.equals(OLD + "test") || path.equals(OLD + "getFlowDatasource")
            || path.equals(OLD + "importSource") || path.equals(OLD + "getSqlEnableDataSourceList");
    }
    public Object handle(String path, HttpServletRequest request, LoginUser user) throws IOException {
        long owner = user.getUserId(); String method = request.getMethod();
        if (path.equals(OLD + "getSqlEnableDataSourceList")) { method(method, "GET"); return ""; }
        if (path.equals(OLD + "importSource")) {
            method(method, "POST"); HdiCoreSessions.require(user, "governance:flow:edit"); JsonNode query = body(request);
            // All selectable sources already belong to this platform user; no external registry import occurs.
            ObjectNode current = connections.get(owner, HdiProtocol.positive(query.path("id").asText(), "连接标识"));
            if (query.has("_platformRevision") && !query.path("_platformRevision").asText().equals(current.path("_platformRevision").asText()))
                throw new HdiTaskFault(409, "连接已被修改，请重新选择");
            return nodeConnection(current);
        }
        if (path.startsWith(OLD + "getDatasourceById/")) {
            method(method, "POST"); return nodeConnection(connections.get(owner, HdiProtocol.positive(path.substring((OLD + "getDatasourceById/").length()), "连接标识")));
        }
        if (path.equals(OLD + "getFlowDatasource")) {
            method(method, "POST"); Map<String,Object> page = new LinkedHashMap<>(connections.page(owner, body(request), number(request, "pageNo", 1), number(request, "pageSize", 20)));
            @SuppressWarnings("unchecked") List<ObjectNode> rows = (List<ObjectNode>)page.get("list");
            page.put("list", rows.stream().map(HdiCoreConnectionApi::nodeConnection).toList()); return page;
        }
        if (path.startsWith(TABLE)) {
            String route = path.substring(TABLE.length());
            if (tableMetadata != null && tableMetadata.handles(route)) {
                method(method, "POST"); return tableMetadata.handle(route, owner, body(request));
            }
            // The original enum contains only incremental modes (CDC/number/string/date/partition).
            // This profile supports full runs; there is no original "full=0" option to fabricate.
            if (route.equals("getTableIncrType")) { method(method, "GET"); return List.of(); }
            if (route.startsWith("getTables/")) {
                method(method, "GET"); long id = HdiProtocol.positive(route.substring("getTables/".length()), "连接标识");
                return tablePage(owner, id, HdiProtocol.JSON.createObjectNode()).get("list");
            }
            if (route.startsWith("getDatabaseColumns/")) {
                method(method, "POST"); return connections.columns(owner, HdiProtocol.positive(route.substring("getDatabaseColumns/".length()), "连接标识"), body(request));
            }
            if (route.equals("listMetadataSchema")) { method(method, "GET"); return connections.schemas(owner, id(request, "dbId")); }
            if (route.equals("getTable")) { method(method, "GET"); return connections.getTable(owner, id(request, "tableId")); }
            if (route.equals("queryTablePage")) {
                method(method, "POST"); ObjectNode query = body(request);
                if (HdiProtocol.query(request, "pageNo") != null) query.put("pageNo", number(request, "pageNo", 1));
                if (HdiProtocol.query(request, "pageSize") != null) query.put("pageSize", number(request, "pageSize", 20));
                return tablePage(owner, HdiProtocol.positive(query.path("dbId").asText(), "连接标识"), query);
            }
            throw unavailable();
        }
        if (path.equals(OLD + "test")) {
            method(method, "POST"); HdiCoreSessions.require(user, "governance:flow:test"); JsonNode query = body(request);
            if (query.hasNonNull("id")) connections.testById(owner, HdiProtocol.positive(query.path("id").asText(), "连接标识"));
            else connections.testForm(owner, query);
            return true;
        }
        if (path.equals("/_platform/connections/usage")) {
            method(method, "GET");
            return Map.of("used", connections.count(owner), "total", 100, "usedDbType", connections.list(owner, null).stream().map(row -> row.path("datasourceType").asText()).distinct().toList());
        }
        if (path.equals(OLD + "getDatasourceOptions")) {
            if (method.equals("GET")) return catalog.getDatasourceTypeOptions();
            method(method, "POST"); return connections.list(owner, body(request)).stream().map(HdiCoreConnectionApi::nodeConnection).toList();
        }
        for (String prefix : List.of(OLD + "getExtInfos/", OLD + "getTables/", OLD + "getDatabaseColumns/")) {
            if (!path.startsWith(prefix)) continue;
            long id = HdiProtocol.positive(path.substring(prefix.length()), "连接标识");
            if (prefix.endsWith("getExtInfos/")) { method(method, "GET"); return connections.get(owner, id); }
            method(method, "POST");
            return prefix.endsWith("getTables/") ? connections.tables(owner, id, body(request)) : connections.columns(owner, id, body(request));
        }
        if (path.equals(OLD + "listMetadataSchema")) {
            method(method, "GET"); return connections.schemas(owner, id(request, "dbId"));
        }
        if (!path.startsWith(NEW)) throw unavailable();
        String route = path.substring(NEW.length());
        if (Set.of("getDatasourceCategories", "getDiscoveryCategories", "getAllCommonDatasources", "deleteCommonDatasource").contains(route)) method(method, "GET"); else method(method, "POST");
        switch (route) {
            case "getAllCommonDatasources": {
                JsonNode preferences = HdiProtocol.JSON.readTree(store.getPreference(owner, "connection-types", "[]"));
                Set<String> names = new java.util.LinkedHashSet<>(); preferences.forEach(value -> names.add(value.asText()));
                return types().stream().filter(row -> names.contains(row.get("name"))).toList();
            }
            case "addCommonDatasource": case "deleteCommonDatasource": {
                String name = HdiProtocol.query(request, "datasourceName");
                if (name == null || !Set.of("PostgreSQL", "MySQL", "FTP", "Kafka", "RabbitMQ").contains(name)) throw bad("连接类型无效");
                Set<String> names = new java.util.LinkedHashSet<>();
                HdiProtocol.JSON.readTree(store.getPreference(owner, "connection-types", "[]")).forEach(value -> names.add(value.asText()));
                if (route.equals("addCommonDatasource")) names.add(name); else names.remove(name);
                store.setPreference(owner, "connection-types", HdiProtocol.JSON.valueToTree(names).toString()); return null;
            }
            case "getDatasourceCategories": return types().stream().map(row -> row.get("datasourceCategory")).distinct().toList();
            case "getDiscoveryCategories": return List.of(); // No vendor service-discovery registry is configured.
            case "getDatasourceTypes": {
                JsonNode query = body(request); String category = query.path("datasourceCategory").asText(""), keyword = query.path("datasourceType").asText("");
                return types().stream().filter(row -> (category.isBlank() || category.equals("全部") || category.equals(row.get("datasourceCategory")))
                    && row.get("name").toString().toLowerCase(java.util.Locale.ROOT).contains(keyword.toLowerCase(java.util.Locale.ROOT))).toList();
            }
            case "getTree": {
                List<Map<String, Object>> result = new ArrayList<>();
                for (Map<String, Object> row : types()) {
                    String category = row.get("datasourceCategory").toString();
                    Map<String, Object> group = result.stream().filter(item -> item.get("name").equals(category)).findFirst().orElse(null);
                    if (group == null) { group = new LinkedHashMap<>(); group.put("name", category); group.put("children", new ArrayList<Map<String, Object>>()); result.add(group); }
                    @SuppressWarnings("unchecked") List<Map<String, Object>> children = (List<Map<String, Object>>)group.get("children"); children.add(Map.of("name", row.get("name")));
                }
                return result;
            }
            case "getDatasourceList": return connections.page(owner, body(request), number(request, "pageNo", 1), number(request, "pageSize", 20));
            case "getDatasourceVersions": return List.of(descriptor(type(request), null).path("datasourceVersion").asText());
            case "getForm": return form(type(request), HdiProtocol.query(request, "datasourceVersion"));
            case "queryDatasource": {
                long id = id(request, "id"); ObjectNode row = connections.get(owner, id);
                ObjectNode view = form(row.path("datasourceType").asText(), row.path("datasourceVersion").asText());
                view.setAll(row); ObjectNode parameters = connections.formParameters(owner, id);
                for (JsonNode item : view.path("form").path("formItems")) if (parameters.has(item.path("prop").asText()))
                    ((ObjectNode)item).set("value", parameters.path(item.path("prop").asText()));
                return view;
            }
            case "getUrl": {
                JsonNode query = body(request); JsonNode parameters = query.path("parameters");
                if (!parameters.isObject()) throw bad("连接参数无效");
                JsonNode result = compiler.connectionDescription("connectionUrl", type(query), query.path("datasourceVersion").asText(""), parameters);
                return Map.of("value", result.path("url").asText());
            }
            case "getSelectValues": return List.of(); // No registered Kerberos profiles or external discovery providers.
            case "addDatasource": case "updateDatasource":
                HdiCoreSessions.require(user, "governance:flow:edit"); return connections.saveForm(owner, formBody(request));
            case "test":
                HdiCoreSessions.require(user, "governance:flow:test"); connections.testForm(owner, formBody(request)); return true;
            case "testById":
                HdiCoreSessions.require(user, "governance:flow:test"); connections.testById(owner, id(request, "prop")); return true;
            case "deleteDatasource": {
                HdiCoreSessions.require(user, "governance:flow:edit");
                String ids = HdiProtocol.query(request, "ids"); if (ids == null || !ids.matches("[1-9][0-9]*(,[1-9][0-9]*){0,99}")) throw bad("请选择明确的连接记录");
                for (String value : ids.split(",")) { long id = HdiProtocol.positive(value, "连接标识"); ObjectNode row = connections.get(owner, id); connections.delete(owner, id, row.path("_platformRevision").asLong()); }
                return null;
            }
            case "getTables": return tablePage(owner, id(request, "datasourceId"), tableQuery(request));
            case "getTableFields": return connections.columns(owner, id(request, "datasourceId"), tableQuery(request));
            default: throw unavailable();
        }
    }
    private List<Map<String, Object>> types() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String type : List.of("PostgreSQL", "MySQL", "FTP", "Kafka", "RabbitMQ")) {
            JsonNode value = descriptor(type, null);
            rows.add(Map.of("name", type, "dbType", value.path("dbType").intValue(), "datasourceCategory", value.path("category").asText()));
        }
        return rows;
    }
    private Map<String, Object> tablePage(long owner, long id, JsonNode query) {
        Map<String, Object> page = new LinkedHashMap<>(connections.tables(owner, id, query));
        @SuppressWarnings("unchecked") List<Map<String, Object>> observed = (List<Map<String, Object>>)page.get("list");
        page.put("list", observed.stream().map(row -> {
            Map<String, Object> value = new LinkedHashMap<>(row);
            // These are actual metadata rows discovered through an enabled, owner-bound connection.
            // Query/write privileges remain enforced by that database account during execution.
            value.put("enable", true); value.put("disable", false); return value;
        }).toList());
        return page;
    }
    private JsonNode descriptor(String type, String version) {
        String normalized = type.equalsIgnoreCase("postgresql") ? "PostgreSQL" : type;
        if (!Set.of("PostgreSQL", "MySQL", "FTP", "Kafka", "RabbitMQ").contains(normalized)) throw bad("当前未接入此连接类型");
        String key = normalized + ":" + (version == null || version.equals("undefined") ? "" : version);
        return descriptors.computeIfAbsent(key, ignored -> catalog.translateConnectionForm(compiler.connectionDescription("connectionForm", normalized,
            version == null || version.equals("undefined") ? "" : version, null))).deepCopy();
    }
    private ObjectNode form(String type, String version) {
        ObjectNode value = (ObjectNode)descriptor(type, version);
        value.put("datasourceCategory", value.path("category").asText()); value.put("datasourceName", ""); value.put("folderId", -1); value.putArray("relations");
        value.remove(List.of("ok", "operation", "protocolVersion")); return value;
    }
    private static ObjectNode body(HttpServletRequest request) throws IOException {
        if (request.getContentLengthLong() == 0) return HdiProtocol.JSON.createObjectNode();
        JsonNode value = HdiProtocol.body(request, 128 * 1024); if (!value.isObject()) throw bad("连接请求必须是对象"); return (ObjectNode)value;
    }
    private static ObjectNode nodeConnection(ObjectNode source) {
        // Native node selection copies this value into NodeVo.dbType, whose UI contract is String.
        // Keep the connection management model and persisted execution DTO as their original integer type.
        ObjectNode result = source.deepCopy(); result.put("dbType", source.path("dbType").asText()); return result;
    }
    private static ObjectNode tableQuery(HttpServletRequest request) throws IOException {
        ObjectNode query = body(request);
        for (String name : List.of("tableName", "tableSchem", "tableCat", "schemaCode")) {
            String value = HdiProtocol.query(request, name); if (value != null) query.put(name, value);
        }
        return query;
    }
    private static ObjectNode formBody(HttpServletRequest request) throws IOException {
        if (request.getContentType() == null || !request.getContentType().startsWith("multipart/form-data")) return body(request);
        if (request.getContentLengthLong() > 128 * 1024) throw bad("连接表单过大");
        ObjectNode value = HdiProtocol.JSON.createObjectNode();
        for (Map.Entry<String, String[]> field : request.getParameterMap().entrySet()) {
            if (field.getValue().length != 1 || field.getValue()[0].length() > 32768) throw bad("连接参数重复或过长");
            if (field.getKey().equals("parameters")) {
                JsonNode parameters = HdiProtocol.JSON.readTree(field.getValue()[0]); if (parameters == null || !parameters.isObject()) throw bad("连接参数无效"); value.set("parameters", parameters);
            } else value.put(field.getKey(), field.getValue()[0]);
        }
        try { for (var part : request.getParts()) if (part.getSubmittedFileName() != null) throw bad("当前连接不接受附加文件"); }
        catch (jakarta.servlet.ServletException invalid) { throw bad("连接表单无效"); }
        return value;
    }
    private static String type(HttpServletRequest request) { String value = HdiProtocol.query(request, "datasourceType"); if (value == null) throw bad("缺少连接类型"); return value; }
    private static String type(JsonNode value) { String type = value.path("datasourceType").asText(); if (type.isBlank()) throw bad("缺少连接类型"); return type; }
    private static long id(HttpServletRequest request, String key) { return HdiProtocol.positive(HdiProtocol.query(request, key), "连接标识"); }
    private static int number(HttpServletRequest request, String key, int fallback) { String value = HdiProtocol.query(request, key); return value == null ? fallback : Math.toIntExact(HdiProtocol.positive(value, "分页参数")); }
    private static void method(String value, String expected) { if (!value.equals(expected)) throw new HdiTaskFault(405, "连接接口请求方法无效"); }
    private static HdiTaskFault bad(String message) { return new HdiTaskFault(400, message); }
    private static HdiTaskFault unavailable() { return new HdiTaskFault(422, "该连接管理操作尚未接入平台"); }
}
