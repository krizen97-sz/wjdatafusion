package com.hm.manage.service.governance;

import com.fasterxml.jackson.databind.JsonNode;
import com.hm.common.exception.ServiceException;
import com.hm.manage.service.governance.DataGovernanceModels.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import static com.hm.manage.service.governance.DataGovernanceEngine.*;

/** Platform authoring of stopped safe drafts. Every mutation retains NiFi's entity revision check. */
@Service
public class DataGovernanceDesigner
{
    private static final Set<String> GENERATOR_KEYS = Set.of("Custom Text", "Batch Size", "Data Format", "Unique FlowFiles", "File Size", "Character Set", "Mime Type");
    private static final Set<String> JSON_KEYS = Set.of("Destination", "Return Type", "Path Not Found Behavior", "Null Value Representation", "Max String Length");
    private static final Set<String> UPDATE_KEYS = Set.of("Delete Attributes Expression", "Store State", "Stateful Variables Initial Value", "Cache Value Lookup Cache Size");
    private static final Set<String> JOLT_KEYS = Set.of("Jolt Specification", "Jolt Transform", "Pretty Print", "Transform Cache Size", "Max String Length", "JSON Source", "Retain Unicode Escape Sequences");
    private static final Set<String> WRITER_KEYS = Set.of("Field Order", "Delimiter Hex", "Include Header", "Split Limit", "Count Basis", "Maximum File Age Millis", "Arrival Time Field", "Filename Prefix");
    private static final Set<String> LOOKUP_KEYS = Set.of("Lookup Rows", "Match Fields", "Return Fields", "Missing Match", "Multiple Matches");
    private final DataGovernanceEngine engine;
    private final DataGovernanceNifiClient client;

    public DataGovernanceDesigner(DataGovernanceEngine engine) { this.engine = engine; this.client = engine.client; }

    public List<DesignNodeType> nodeTypes()
    {
        return List.of(
            new DesignNodeType("source", "样本输入", STANDARD + "GenerateFlowFile", "PROCESSOR", strings("Custom Text", "{\"message\":\"sample\"}", "Batch Size", "1", "Data Format", "Text", "Unique FlowFiles", "false"), List.of("success")),
            new DesignNodeType("jsonpath", "字段提取", STANDARD + "EvaluateJsonPath", "PROCESSOR", strings("Destination", "flowfile-attribute", "Return Type", "auto-detect", "sample.value", "$.message"), List.of("matched", "unmatched", "failure")),
            new DesignNodeType("route", "条件分支", STANDARD + "RouteOnAttribute", "PROCESSOR", strings("Routing Strategy", "Route to Property name", "accepted", "${sample.value:isEmpty():not()}"), List.of("accepted", "unmatched")),
            new DesignNodeType("attributes", "字段加工", UPDATE, "PROCESSOR", strings("sample.result", "${sample.value:trim()}"), List.of("success")),
            new DesignNodeType("jolt", "JSON 转换", JOLT, "PROCESSOR", strings("Jolt Transform", "jolt-transform-chain", "Jolt Specification", "[{\"operation\":\"shift\",\"spec\":{\"*\":\"&\"}}]", "JSON Source", "FLOW_FILE"), List.of("success", "failure")),
            new DesignNodeType("delimited", "协议文本输出", WRITER, "PROCESSOR", strings("Field Order", "message,picture", "Delimiter Hex", "7C 1F", "Include Header", "true", "Split Limit", "75", "Count Basis", "KETTLE_HEADER_INCLUSIVE", "Maximum File Age Millis", "0", "Filename Prefix", "sample"), List.of("success", "empty", "failure")),
            new DesignNodeType("lookup", "快照查表", LOOKUP, "PROCESSOR", strings("Lookup Rows", "[]", "Match Fields", "[]", "Return Fields", "[]", "Missing Match", "KEEP", "Multiple Matches", "FAIL"), List.of("success", "empty", "failure")),
            new DesignNodeType("capture", "结果观察", UPDATE, "CAPTURE", Map.of(), List.of()));
    }

    public Design design(String flowId)
    {
        Draft draft = load(flowId);
        boolean editable = draft.issues.isEmpty();
        List<DesignNode> nodes = new ArrayList<>();
        for (JsonNode entity : draft.nodes.values()) nodes.add(node(entity, editable));
        List<DesignConnection> edges = new ArrayList<>();
        for (JsonNode entity : draft.edges.values()) edges.add(connection(entity));
        return new Design(flowId, nodes, edges, editable, List.copyOf(draft.issues));
    }

    public synchronized DesignNode createNode(String flowId, CreateDesignNode request)
    {
        Draft draft = writable(flowId);
        if (draft.nodes.size() >= 12) reject("一个样本流程最多支持 12 个节点");
        if (request == null || !DataGovernanceSafeFlow.TYPES.contains(request.type())) reject("组件类型尚未开放平台编辑");
        String role = request.role() == null ? "PROCESSOR" : request.role();
        if (!Set.of("PROCESSOR", "CAPTURE").contains(role) || (role.equals("CAPTURE") && !UPDATE.equals(request.type()))) reject("节点角色无效");
        if (role.equals("CAPTURE") && draft.nodes.values().stream().anyMatch(p -> CAPTURE.equals(processorComment(p.path("component"))))) reject("流程只能有一个结果观察节点");
        if (request.type().equals(STANDARD + "GenerateFlowFile") && draft.nodes.values().stream().anyMatch(p -> request.type().equals(p.path("component").path("type").asText()))) reject("流程只能有一个样本输入节点");
        DesignPosition position = position(request.position());
        String name = text(request.name(), 80, "节点名称");
        Map<String, String> values = defaults(request.type(), role);
        if (request.properties() != null) values.putAll(request.properties());
        String comments = role.equals("CAPTURE") ? CAPTURE : "";
        JsonNode candidate = candidate(request.type(), comments, values);
        validate(candidate);
        JsonNode bundle = null;
        for (JsonNode type : client.json("GET", "/flow/processor-types", null).path("processorTypes"))
            if (request.type().equals(type.path("type").asText())) { bundle = type.path("bundle"); break; }
        if (bundle == null) reject("独立引擎尚未安装该组件");
        JsonNode created = client.json("POST", "/process-groups/" + id(flowId) + "/processors", map("revision", map("version", 0),
            "component", map("name", name, "type", request.type(), "bundle", bundle, "position", position,
                "config", map("comments", comments, "properties", values, "schedulingStrategy", "TIMER_DRIVEN", "schedulingPeriod", "0 sec",
                    "concurrentlySchedulableTaskCount", 1, "autoTerminatedRelationships", role.equals("CAPTURE") ? List.of("success") : List.of()))));
        return node(created, true);
    }

    public synchronized DesignNode updateNode(String flowId, String nodeId, UpdateDesignNode request)
    {
        Draft draft = writable(flowId);
        JsonNode existing = member(draft.nodes, nodeId, "节点");
        if (request == null) reject("节点修改不能为空");
        revision(existing, request.version());
        JsonNode old = existing.path("component");
        Map<String, Object> component = map("id", nodeId);
        if (request.name() != null) component.put("name", text(request.name(), 80, "节点名称"));
        if (request.position() != null) component.put("position", position(request.position()));
        if (request.properties() != null)
        {
            Map<String, String> values = new LinkedHashMap<>(request.properties());
            validate(candidate(old.path("type").asText(), processorComment(old), values));
            // NiFi properties are patch semantics. Explicit null removes dynamic properties omitted by the editor.
            Map<String, String> patch = new LinkedHashMap<>(values);
            old.path("config").path("properties").fieldNames().forEachRemaining(key -> { if (!values.containsKey(key)) patch.put(key, null); });
            component.put("config", map("properties", patch));
        }
        if (component.size() == 1) reject("节点修改不能为空");
        JsonNode updated = client.json("PUT", "/processors/" + id(nodeId), map("revision", map("version", request.version()), "component", component));
        return node(updated, true);
    }

    public synchronized Map<String, Boolean> deleteNode(String flowId, String nodeId, Long version)
    {
        Draft draft = writable(flowId);
        revision(member(draft.nodes, nodeId, "节点"), version);
        for (JsonNode edge : draft.edges.values())
        {
            JsonNode c = edge.path("component");
            if (nodeId.equals(c.path("source").path("id").asText()) || nodeId.equals(c.path("destination").path("id").asText())) reject("请先移除与该节点关联的连接");
        }
        client.json("DELETE", "/processors/" + id(nodeId) + "?version=" + version + "&disconnectedNodeAcknowledged=false", null);
        return Map.of("deleted", true);
    }

    public synchronized DesignConnection createConnection(String flowId, CreateDesignConnection request)
    {
        Draft draft = writable(flowId);
        if (draft.edges.size() >= 24) reject("一个样本流程最多支持 24 条连接");
        if (request == null) reject("连接不能为空");
        JsonNode from = member(draft.nodes, request.sourceId(), "来源节点").path("component");
        JsonNode to = member(draft.nodes, request.targetId(), "目标节点").path("component");
        if (request.sourceId().equals(request.targetId()) || CAPTURE.equals(processorComment(from)) || (STANDARD + "GenerateFlowFile").equals(to.path("type").asText())) reject("连接方向无效，输入节点不能接收数据，结果观察节点不能输出数据");
        if (!relationships(from).contains(request.relationship())) reject("来源节点不支持所选关系，请刷新节点配置");
        for (JsonNode edge : draft.edges.values())
        {
            JsonNode c = edge.path("component");
            if (request.sourceId().equals(c.path("source").path("id").asText()) && request.targetId().equals(c.path("destination").path("id").asText())
                && contains(c.path("selectedRelationships"), request.relationship())) reject("该关系连接已存在");
        }
        if (reaches(draft, request.targetId(), request.sourceId(), new HashSet<>())) reject("样本流程暂不支持循环连接");
        return connection(engine.connect(flowId, request.sourceId(), request.targetId(), List.of(request.relationship())));
    }

    public synchronized Map<String, Boolean> deleteConnection(String flowId, String connectionId, Long version)
    {
        Draft draft = writable(flowId);
        revision(member(draft.edges, connectionId, "连接"), version);
        client.json("DELETE", "/connections/" + id(connectionId) + "?version=" + version + "&disconnectedNodeAcknowledged=false", null);
        return Map.of("deleted", true);
    }

    private Draft load(String flowId)
    {
        engine.flow(flowId); // Includes flow marker, project marker, and configured-root ancestry checks.
        JsonNode flow = engine.groupContents(flowId);
        Draft draft = new Draft();
        for (String kind : List.of("processGroups", "remoteProcessGroups", "inputPorts", "outputPorts", "funnels"))
            if (flow.path(kind).size() != 0) draft.issue("流程包含子组、外部连接或端口，当前仅支持查看");
        JsonNode services = client.json("GET", "/flow/process-groups/" + id(flowId) + "/controller-services?includeAncestorGroups=false&includeDescendantGroups=false", null);
        if (!services.path("controllerServices").isArray() || services.path("controllerServices").size() != 0) draft.issue("流程包含连接服务或无法确认服务状态，当前仅支持查看");
        for (JsonNode entity : flow.path("processors"))
        {
            JsonNode p = entity.path("component"); String nodeId = p.path("id").asText();
            draft.nodes.put(nodeId, entity);
            if (!flowId.equals(p.path("parentGroupId").asText())) draft.issue("流程节点归属无法确认");
            if (!"STOPPED".equals(p.path("state").asText()) || entity.path("status").path("aggregateSnapshot").path("activeThreadCount").asLong(-1) != 0
                || entity.path("status").path("aggregateSnapshot").path("terminatedThreadCount").asLong(0) != 0) draft.issue("请先停止流程全部节点并等待运行线程结束");
            try { validate(p); }
            catch (ServiceException e) { draft.issue("流程包含尚未支持的组件配置，当前仅支持查看"); }
        }
        for (JsonNode entity : flow.path("connections"))
        {
            JsonNode c = entity.path("component");
            draft.edges.put(c.path("id").asText(), entity);
            if (!flowId.equals(c.path("parentGroupId").asText()) || !draft.nodes.containsKey(c.path("source").path("id").asText()) || !draft.nodes.containsKey(c.path("destination").path("id").asText())
                || !"PROCESSOR".equals(c.path("source").path("type").asText()) || !"PROCESSOR".equals(c.path("destination").path("type").asText())
                || c.path("selectedRelationships").size() != 1) draft.issue("流程包含不受支持的连接，当前仅支持查看");
            if (entity.path("status").path("aggregateSnapshot").path("flowFilesQueued").asLong(-1) != 0 || entity.path("status").path("aggregateSnapshot").path("bytesQueued").asLong(-1) != 0) draft.issue("流程仍有排队数据，清空队列后才能编辑");
        }
        return draft;
    }

    private Draft writable(String flowId)
    { Draft draft = load(flowId); if (!draft.issues.isEmpty()) reject(draft.issues.get(0)); return draft; }

    private DesignNode node(JsonNode entity, boolean graphEditable)
    {
        JsonNode p = entity.path("component"); List<String> issues = new ArrayList<>(); Map<String, String> values = new LinkedHashMap<>();
        boolean safe = true;
        try { validate(p); }
        catch (ServiceException e) { safe = false; issues.add("该组件配置暂不支持平台编辑"); }
        if (safe) p.path("config").path("properties").fields().forEachRemaining(entry -> {
            if (allowedKey(p.path("type").asText(), entry.getKey()) && !p.path("config").path("descriptors").path(entry.getKey()).path("sensitive").asBoolean())
                values.put(entry.getKey(), entry.getValue().isNull() ? null : entry.getValue().asText());
        });
        if ("INVALID".equals(p.path("validationStatus").asText())) issues.add("节点属性或连线尚未完整，完成配置后可测试");
        boolean capture = CAPTURE.equals(processorComment(p));
        return new DesignNode(p.path("id").asText(), entity.path("revision").path("version").asLong(), p.path("name").asText(), p.path("type").asText(), capture ? "CAPTURE" : "PROCESSOR",
            point(p.path("position")), values, capture ? List.of() : relationships(p), p.path("state").asText("UNKNOWN"), safe && graphEditable, issues);
    }

    private DesignConnection connection(JsonNode entity)
    {
        JsonNode c = entity.path("component"); List<String> relationships = new ArrayList<>(); c.path("selectedRelationships").forEach(r -> relationships.add(r.asText()));
        List<DesignPosition> bends = new ArrayList<>(); c.path("bends").forEach(b -> bends.add(point(b)));
        return new DesignConnection(c.path("id").asText(), entity.path("revision").path("version").asLong(), c.path("source").path("id").asText(), c.path("destination").path("id").asText(), relationships, bends);
    }

    private JsonNode candidate(String type, String comments, Map<String, String> values)
    { return engine.mapper.valueToTree(map("type", type, "config", map("comments", comments, "properties", values))); }

    private static void validate(JsonNode p)
    {
        String type = p.path("type").asText();
        if (!DataGovernanceSafeFlow.TYPES.contains(type)) reject("组件类型尚未开放平台编辑");
        if (CAPTURE.equals(processorComment(p)) && !type.equals(UPDATE)) reject("结果观察节点角色无效");
        if (!p.path("config").path("annotationData").asText("").isBlank()) reject("暂不支持高级规则配置");
        JsonNode props = p.path("config").path("properties");
        if (!props.isObject() || props.size() > 100) reject("节点属性数量无效");
        if (type.equals(STANDARD + "EvaluateJsonPath") && !"flowfile-attribute".equals(props.path("Destination").asText())) reject("字段提取必须保留输入内容，目标应设为 flowfile-attribute");
        if (type.equals(STANDARD + "GenerateFlowFile") && (!"1".equals(props.path("Batch Size").asText()) || !"Text".equals(props.path("Data Format").asText()) || !"false".equals(props.path("Unique FlowFiles").asText()))) reject("样本输入必须保持单份固定文本配置");
        int total = 0;
        for (var entries = props.fields(); entries.hasNext(); )
        {
            var entry = entries.next(); String key = entry.getKey(); JsonNode raw = entry.getValue();
            if (key.length() > 100) reject("节点属性名称过长");
            total += key.length();
            if (raw.isNull()) continue; // NiFi includes null optional descriptors; they hold no configuration.
            if (!raw.isTextual() || !allowedKey(type, key) || p.path("config").path("descriptors").path(key).path("sensitive").asBoolean()) reject("节点包含未经审核的属性");
            String value = raw.asText(); total += value.length();
            if (value.length() > 65536 || total > 131072 || (!type.equals(LOOKUP) && value.contains("#{"))) reject("节点属性过长或引用了环境参数");
            if (type.equals(STANDARD + "GenerateFlowFile"))
            {
                if (value.contains("${")) reject("样本输入内容必须为纯文本，不允许环境表达式");
                if ((key.equals("Batch Size") && !value.equals("1")) || (key.equals("Data Format") && !value.equals("Text")) || (key.equals("Unique FlowFiles") && !value.equals("false"))) reject("样本输入仅支持单份固定文本");
            }
            if (type.equals(STANDARD + "RouteOnAttribute") && key.equals("Routing Strategy") && !value.equals("Route to Property name")) reject("条件分支仅支持按属性名路由");
        }
        try { DataGovernanceSafeFlow.validateProperties(p); }
        catch (DataGovernanceSafeFlow.UnsupportedFlow e) { throw new ServiceException(e.getMessage()); }
    }

    private static boolean allowedKey(String type, String key)
    {
        if (key == null || key.length() > 100) return false;
        if (type.equals(STANDARD + "GenerateFlowFile")) return GENERATOR_KEYS.contains(key);
        if (type.equals(STANDARD + "EvaluateJsonPath")) return JSON_KEYS.contains(key) || key.matches("sample\\.[A-Za-z0-9_.-]{1,64}");
        if (type.equals(STANDARD + "RouteOnAttribute")) return key.equals("Routing Strategy") || key.matches("[A-Za-z][A-Za-z0-9_.-]{0,63}");
        if (type.equals(UPDATE)) return UPDATE_KEYS.contains(key) || key.matches("sample\\.[A-Za-z0-9_.-]{1,64}");
        if (type.equals(JOLT)) return JOLT_KEYS.contains(key);
        if (type.equals(LOOKUP)) return LOOKUP_KEYS.contains(key);
        return type.equals(WRITER) && WRITER_KEYS.contains(key);
    }

    private Map<String, String> defaults(String type, String role)
    { return new LinkedHashMap<>(nodeTypes().stream().filter(n -> n.type().equals(type) && n.role().equals(role)).findFirst().orElseThrow().properties()); }
    private static Map<String, String> strings(String... pairs)
    { Map<String, String> result = new LinkedHashMap<>(); for (int i = 0; i < pairs.length; i += 2) result.put(pairs[i], pairs[i + 1]); return result; }
    private static List<String> relationships(JsonNode p)
    { List<String> result = new ArrayList<>(); p.path("relationships").forEach(r -> result.add(r.path("name").asText())); return result; }
    private static JsonNode member(Map<String, JsonNode> values, String id, String kind)
    { DataGovernanceEngine.id(id); JsonNode found = values.get(id); if (found == null) reject(kind + "不属于当前流程"); return found; }
    private static void revision(JsonNode entity, Long version)
    { if (version == null || version < 0) reject("请提供节点或连接的当前版本"); if (version != entity.path("revision").path("version").asLong(-1)) throw new ServiceException("画布已被其他操作修改，请刷新后重试", 409); }
    private static DesignPosition position(DesignPosition value)
    { if (value == null || !Double.isFinite(value.x()) || !Double.isFinite(value.y()) || Math.abs(value.x()) > 100000 || Math.abs(value.y()) > 100000) reject("节点位置无效"); return value; }
    private static DesignPosition point(JsonNode value) { return new DesignPosition(value.path("x").asDouble(), value.path("y").asDouble()); }
    private static boolean contains(JsonNode values, String value) { for (JsonNode item : values) if (item.asText().equals(value)) return true; return false; }
    private static boolean reaches(Draft draft, String from, String target, Set<String> seen)
    {
        if (from.equals(target)) return true; if (!seen.add(from)) return false;
        for (JsonNode edge : draft.edges.values()) { JsonNode c = edge.path("component"); if (from.equals(c.path("source").path("id").asText()) && reaches(draft, c.path("destination").path("id").asText(), target, seen)) return true; }
        return false;
    }
    private static void reject(String message) { throw new ServiceException(message); }
    private static final class Draft
    {
        final Map<String, JsonNode> nodes = new LinkedHashMap<>(); final Map<String, JsonNode> edges = new LinkedHashMap<>(); final List<String> issues = new ArrayList<>();
        void issue(String message) { if (!issues.contains(message)) issues.add(message); }
    }
}
