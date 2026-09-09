package com.hm.manage.service.governance;

import com.fasterxml.jackson.databind.JsonNode;
import com.hm.common.exception.ServiceException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import static com.hm.manage.service.governance.DataGovernanceEngine.*;

/** Fail closed: the current saved graph is inspected before any execution clone is created. */
final class DataGovernanceSafeFlow
{
    static final Set<String> TYPES = Set.of(STANDARD + "GenerateFlowFile", STANDARD + "EvaluateJsonPath",
        STANDARD + "RouteOnAttribute", JOLT, UPDATE, WRITER);
    final Map<String, JsonNode> processors = new LinkedHashMap<>();
    final List<JsonNode> connections = new ArrayList<>();
    final List<String> order = new ArrayList<>();
    String source;
    String capture;

    DataGovernanceSafeFlow(JsonNode flow)
    {
        for (String key : List.of("processGroups", "remoteProcessGroups", "inputPorts", "outputPorts", "controllerServices", "funnels"))
            if (flow.path(key).size() > 0) unsupported("样本测试暂不支持子流程、端口、连接服务或外部系统");
        for (JsonNode entity : flow.path("processors"))
        {
            JsonNode p = entity.path("component");
            String type = p.path("type").asText();
            if (!TYPES.contains(type)) unsupported("当前画布包含不在样本测试安全白名单中的组件");
            String id = id(p.path("id").asText());
            processors.put(id, p);
            if (type.equals(STANDARD + "GenerateFlowFile"))
            {
                if (source != null) unsupported("样本测试只允许一个样本输入节点");
                source = id;
            }
            if (CAPTURE.equals(processorComment(p)))
            {
                if (capture != null || !type.equals(UPDATE)) unsupported("样本观察节点无效");
                capture = id;
            }
            validateProperties(p);
        }
        if (processors.size() < 2 || processors.size() > 12 || source == null || capture == null)
            unsupported("样本测试需要 2 至 12 个安全节点、一个样本输入和一个结果观察节点");
        Map<String, Integer> degrees = new HashMap<>();
        Map<String, List<String>> targets = new HashMap<>();
        processors.keySet().forEach(p -> degrees.put(p, 0));
        for (JsonNode entity : flow.path("connections"))
        {
            JsonNode c = entity.path("component");
            String from = c.path("source").path("id").asText();
            String to = c.path("destination").path("id").asText();
            if (!processors.containsKey(from) || !processors.containsKey(to) || from.equals(capture) || to.equals(source)
                || !"PROCESSOR".equals(c.path("source").path("type").asText())
                || !"PROCESSOR".equals(c.path("destination").path("type").asText())
                || c.path("selectedRelationships").size() != 1)
                unsupported("样本测试仅支持模块内处理器连接，每条连接必须对应一个明确关系");
            connections.add(c); degrees.merge(to, 1, Integer::sum);
            targets.computeIfAbsent(from, key -> new ArrayList<>()).add(to);
        }
        if (connections.size() > 24) unsupported("样本测试连接数超过上限");
        List<String> ready = new ArrayList<>();
        degrees.forEach((p, n) -> { if (n == 0) ready.add(p); });
        if (ready.size() != 1 || !ready.contains(source)) unsupported("画布包含未连接到样本输入的节点");
        for (int i = 0; i < ready.size(); i++)
        {
            String p = ready.get(i); order.add(p);
            for (String target : targets.getOrDefault(p, List.of()))
                if (degrees.merge(target, -1, Integer::sum) == 0) ready.add(target);
        }
        if (order.size() != processors.size()) unsupported("样本测试暂不支持循环流程");
        for (String p : processors.keySet())
            if (!p.equals(capture) && !targets.containsKey(p)) unsupported("每个业务节点必须连接到可观察的结果路径");
    }

    /** Persist only the safe configuration that will actually run, never arbitrary canvas metadata. */
    JsonNode freeze(String inputJson, Map<String, Object> parameters)
    {
        List<Map<String, Object>> nodes = new ArrayList<>();
        for (String id : processors.keySet().stream().sorted().toList())
        {
            JsonNode p = processors.get(id);
            Map<String, Object> properties = new TreeMap<>();
            p.path("config").path("properties").fields().forEachRemaining(entry ->
                properties.put(entry.getKey(), entry.getValue().isNull() ? null : entry.getValue().asText()));
            if (id.equals(source))
            {
                String encoded = Base64.getEncoder().encodeToString(inputJson.getBytes(StandardCharsets.UTF_8));
                properties.clear();
                properties.putAll(map("Custom Text", "${literal('" + encoded + "'):base64Decode()}", "Batch Size", "1",
                    "Data Format", "Text", "Unique FlowFiles", "false"));
            }
            if (p.path("type").asText().equals(STANDARD + "EvaluateJsonPath") && parameters.containsKey("jsonPath"))
                properties.put("sample.value", parameters.get("jsonPath"));
            if (p.path("type").asText().equals(STANDARD + "RouteOnAttribute") && parameters.containsKey("requiredValue"))
            {
                String required = (String) parameters.get("requiredValue");
                properties.put("accepted", required.isEmpty() ? "${sample.value:isEmpty():not()}"
                    : "${sample.value:equals('" + required.replace("\\", "\\\\").replace("'", "\\'") + "')}");
            }
            Map<String, Object> bundle = new TreeMap<>();
            for (String field : List.of("group", "artifact", "version"))
            {
                String value = p.path("bundle").path(field).asText("");
                if (value.isBlank()) unsupported("引擎未返回组件版本，无法冻结可复现快照");
                bundle.put(field, value);
            }
            List<String> terminated = new ArrayList<>();
            p.path("config").path("autoTerminatedRelationships").forEach(value -> terminated.add(value.asText()));
            terminated.sort(String::compareTo);
            nodes.add(map("component", map("id", id, "name", p.path("name").asText(), "type", p.path("type").asText(), "bundle", bundle,
                "config", map("comments", id.equals(capture) ? CAPTURE : "", "properties", properties,
                    "autoTerminatedRelationships", terminated, "schedulingStrategy", "TIMER_DRIVEN", "schedulingPeriod", "0 sec", "concurrentlySchedulableTaskCount", 1))));
        }
        List<Map<String, Object>> edges = new ArrayList<>();
        for (JsonNode c : connections.stream().sorted(java.util.Comparator.comparing(connection -> connection.path("id").asText())).toList())
            edges.add(map("component", map("source", map("id", c.path("source").path("id").asText(), "type", "PROCESSOR"),
                "destination", map("id", c.path("destination").path("id").asText(), "type", "PROCESSOR"),
                "selectedRelationships", List.of(c.path("selectedRelationships").get(0).asText()),
                "backPressureObjectThreshold", 200, "backPressureDataSizeThreshold", "2 MB")));
        JsonNode snapshot = new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(map("processors", nodes, "connections", edges));
        new DataGovernanceSafeFlow(snapshot); // Parameter overrides receive the same validation as the saved canvas.
        return snapshot;
    }

    static String hash(JsonNode definition)
    {
        try
        {
            byte[] bytes = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(definition);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        }
        catch (Exception e) { throw new ServiceException("测试定义哈希计算失败"); }
    }

    private void validateProperties(JsonNode processor)
    {
        String type = processor.path("type").asText();
        if (type.equals(STANDARD + "GenerateFlowFile")) return; // Sample text and generator settings are replaced, never evaluated from the saved source.
        JsonNode properties = processor.path("config").path("properties");
        boolean isCapture = CAPTURE.equals(processorComment(processor));
        properties.fields().forEachRemaining(entry -> {
            if (entry.getValue().isNull()) return;
            String key = entry.getKey(), value = entry.getValue().asText();
            if (value.length() > 65536 || value.contains("#{")) unsupported("测试不允许环境参数引用或超长组件属性");
            if (type.equals(WRITER))
            {
                if (!Set.of("Field Order", "Delimiter Hex", "Include Header", "Split Limit", "Count Basis", "Maximum File Age Millis", "Arrival Time Field", "Filename Prefix").contains(key)
                    || value.contains("${")) unsupported("协议文本组件仅允许已审核属性，禁止环境表达式");
                return;
            }
            if (type.equals(UPDATE) && Set.of("Delete Attributes Expression", "Store State", "Stateful Variables Initial Value", "Cache Value Lookup Cache Size").contains(key))
            {
                if (key.equals("Store State") && !value.equals("Do not store state")) unsupported("样本属性组件不能保存引擎共享状态");
                if (key.equals("Cache Value Lookup Cache Size") && !value.matches("[0-9]{1,4}")) unsupported("属性缓存大小无效");
                if (Set.of("Delete Attributes Expression", "Stateful Variables Initial Value").contains(key) && !value.isBlank()) unsupported("样本属性组件不能配置删除表达式或共享初始状态");
                return;
            }
            if (isCapture && !value.isEmpty()) unsupported("结果观察节点必须保持无处理属性");
            if (type.equals(STANDARD + "EvaluateJsonPath"))
            {
                if (value.contains("${")) unsupported("JSON 路径测试不允许环境表达式");
                if (Set.of("Destination", "Return Type", "Path Not Found Behavior", "Null Value Representation", "Max String Length").contains(key))
                {
                    if (key.equals("Destination") && !value.equals("flowfile-attribute")) unsupported("样本字段提取必须保留输入内容");
                }
                else if (!key.matches("sample\\.[A-Za-z0-9_.-]{1,64}") || !value.startsWith("$") || value.length() > 256)
                    unsupported("JSON 提取只支持 sample.* 字段和有限 JSONPath");
            }
            else if (type.equals(JOLT))
            {
                if (!Set.of("Jolt Specification", "Jolt Transform", "Pretty Print", "Transform Cache Size", "Max String Length",
                    "JSON Source", "Retain Unicode Escape Sequences").contains(key))
                    unsupported("不支持自定义 Jolt 类、模块目录或连接服务");
                if (value.contains("${")) unsupported("Jolt 测试不允许环境表达式");
                if (key.equals("Jolt Transform") && !value.equals("jolt-transform-chain"))
                    unsupported("样本测试仅支持 Jolt Chain 的内置操作，高级模式尚未支持");
                if (key.equals("JSON Source") && !value.equals("FLOW_FILE"))
                    unsupported("Jolt 样本测试仅支持 FlowFile JSON 内容");
                // NiFi also accepts a local file path here. Parse every spec as inline JSON before cloning.
                if (key.equals("Jolt Specification"))
                {
                    try
                    {
                        JsonNode chain = new com.fasterxml.jackson.databind.ObjectMapper()
                            .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(value);
                        if (!chain.isArray() || chain.size() > 20) unsupported("Jolt Chain 必须为不超过 20 个操作的数组");
                        for (JsonNode operation : chain)
                            if (!Set.of("shift", "default", "remove", "cardinality", "sort").contains(operation.path("operation").asText()))
                                unsupported("Jolt Chain 不允许自定义类操作");
                    }
                    catch (UnsupportedFlow e) { throw e; }
                    catch (Exception e) { unsupported("Jolt 规范不是有效的安全 JSON"); }
                }
            }
            else
            {
                if (type.equals(UPDATE) && !key.startsWith("sample.") && !value.isBlank())
                    unsupported("属性测试仅允许修改 sample.* 字段");
                if (value.contains("${"))
                {
                    if (!value.matches("\\$\\{sample\\.[A-Za-z0-9_.-]+(?::(?:isEmpty|not|equals|toUpper|toLower|trim)\\((?:'[^'{}$]*')?\\))*}"))
                        unsupported("表达式仅允许对 sample.* 字段做有限无外部访问的操作");
                }
            }
        });
        if (!isCapture)
            for (JsonNode rel : processor.path("config").path("autoTerminatedRelationships"))
                if (rel.asText().equals("failure")) unsupported("失败关系必须接到可观察队列，不能静默终止");
    }
    static void unsupported(String message) { throw new UnsupportedFlow(message); }
    static class UnsupportedFlow extends RuntimeException
    { UnsupportedFlow(String message) { super(message); } }
}
