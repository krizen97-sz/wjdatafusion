package com.hm.manage.service.governance;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hm.governance.compatibility.DelimitedBatchEncoder;
import com.hm.governance.compatibility.JsonRecordTransform;
import com.hm.governance.compatibility.LookupSnapshot;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;

/** Bounded in-memory data flow. No script, expression, filesystem, network or external engine. */
@Service
public final class DataGovernanceLocalEngine {
    public static final int MAX_ROWS = 1000, MAX_NODES = 32, MAX_EDGES = 96, MAX_FIELDS = 64;
    public static final int MAX_INPUT_BYTES = 1024 * 1024, MAX_OUTPUT_BYTES = 2 * 1024 * 1024;
    private static final Set<String> TYPES = Set.of("CSV_INPUT", "JSON_INPUT", "FIELD_MAP", "FILTER", "SORT", "DEDUP", "LOOKUP", "UNION", "OUTPUT");
    private static final ObjectMapper JSON = new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).enable(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS);
    private static final JsonRecordTransform.Codec CODEC = new JsonRecordTransform.Codec() {
        public Object parse(String text) { return json(text); }
        public String stringify(Object value) { return encodeJson(value); }
    };
    private static final CSVFormat CSV_OUTPUT = CSVFormat.RFC4180.builder().setRecordSeparator("").get();

    public record Result(List<Map<String, Object>> steps, List<Map<String, Object>> outputs, int inputRows, int outputRows) {
        public Result { steps = List.copyOf(steps); outputs = List.copyOf(outputs); }
    }
    public static final class ExecutionException extends IllegalArgumentException {
        private final String nodeId;
        public ExecutionException(String nodeId, String message) { super(message); this.nodeId = nodeId; }
        public String nodeId() { return nodeId; }
        public String getNodeId() { return nodeId; }
    }
    private record Node(String id, String name, String type, Map<String, Object> config) {}
    private record Plan(Map<String, Node> nodes, List<String> order, Map<String, List<String>> parents,
                        Map<String, List<String>> children, int edgeCount) {}
    private record Data(List<Map<String, Object>> rows, List<String> fields) {}

    /** Validates a complete runnable graph; the storage layer may separately save incomplete drafts. */
    public Map<String, Object> validate(Map<String, Object> graph) {
        List<Map<String, Object>> errors = new ArrayList<>();
        int nodeCount = 0, edgeCount = 0;
        try {
            Plan plan = plan(graph);
            nodeCount = plan.nodes.size(); edgeCount = plan.edgeCount;
            complete(plan);
            int inputBytes = 0, inputRows = 0;
            for (Node node : plan.nodes.values()) {
                try {
                    config(node);
                    if (input(node)) {
                        String content = content(node.config);
                        inputBytes += utf8(content);
                        if (inputBytes > MAX_INPUT_BYTES) throw invalid("输入内容总量最多 1 MiB");
                        inputRows += readInput(node, () -> false).rows.size();
                        if (inputRows > MAX_ROWS) throw invalid("输入总记录数最多 1000 条");
                    }
                } catch (IllegalArgumentException error) { errors.add(error(node.id, error.getMessage())); }
            }
        } catch (ExecutionException error) { errors.add(error(error.nodeId(), error.getMessage())); }
        catch (IllegalArgumentException error) { errors.add(error("", error.getMessage())); }
        return map("valid", errors.isEmpty(), "errors", errors, "nodeCount", nodeCount, "connectionCount", edgeCount);
    }

    /** Preview runs only the target's ancestors; outputs are never materialized for previews. */
    public Result execute(Map<String, Object> graph, String previewNodeId, int sampleLimit, BooleanSupplier cancelled) {
        return execute(graph, previewNodeId, sampleLimit, cancelled, step -> {});
    }

    public Result execute(Map<String, Object> graph, String previewNodeId, int sampleLimit, BooleanSupplier cancelled,
                          Consumer<Map<String, Object>> stepListener) {
        if (sampleLimit < 1 || sampleLimit > 200) throw invalid("样本行数应为 1–200");
        BooleanSupplier stop = cancelled == null ? () -> false : cancelled;
        Plan plan = plan(graph);
        boolean preview = previewNodeId != null && !previewNodeId.isBlank();
        Set<String> scope;
        if (preview) {
            if (!plan.nodes.containsKey(previewNodeId)) throw new ExecutionException(previewNodeId, "预览节点不存在");
            scope = ancestors(plan, previewNodeId);
        } else { complete(plan); scope = plan.nodes.keySet(); }
        Map<String, Data> data = new LinkedHashMap<>();
        List<Map<String, Object>> steps = new ArrayList<>(), outputs = new ArrayList<>();
        int inputBytes = 0, totalInputRows = 0, totalOutputRows = 0, totalOutputBytes = 0;
        for (String id : plan.order) {
            if (!scope.contains(id)) continue;
            Node node = plan.nodes.get(id);
            try {
                check(stop, id);
                config(node);
                List<Data> incoming = new ArrayList<>();
                for (String parent : plan.parents.get(id)) incoming.add(data.get(parent));
                int inputRows = incoming.stream().mapToInt(d -> d.rows.size()).sum();
                Data result;
                if (input(node)) {
                    inputBytes += utf8(content(node.config));
                    if (inputBytes > MAX_INPUT_BYTES) throw invalid("输入内容总量最多 1 MiB");
                    result = readInput(node, stop);
                    totalInputRows += result.rows.size();
                    if (totalInputRows > MAX_ROWS) throw invalid("输入总记录数最多 1000 条");
                } else if (node.type.equals("UNION")) {
                    result = union(incoming, stop, id);
                } else {
                    if (incoming.size() != 1) throw invalid("该节点必须连接一个上游节点");
                    result = process(node, incoming.get(0), stop);
                }
                bound(result.rows);
                data.put(id, result);
                List<Map<String, Object>> samples = new ArrayList<>();
                for (int i = 0; i < Math.min(sampleLimit, result.rows.size()); i++) {
                    check(stop, id); samples.add(object(copy(result.rows.get(i))));
                }
                Map<String, Object> stepResult = map("id", id, "name", node.name, "status", "SUCCEEDED", "inputRows", inputRows,
                    "outputRows", result.rows.size(), "sampleRows", samples, "fields", List.copyOf(result.fields),
                    "sampleTruncated", result.rows.size() > sampleLimit);
                if (node.type.equals("OUTPUT") && !preview) {
                    byte[] bytes = output(node, result, stop);
                    totalOutputRows += result.rows.size(); totalOutputBytes += bytes.length;
                    if (totalOutputRows > MAX_ROWS || totalOutputBytes > MAX_OUTPUT_BYTES) throw invalid("输出总量超过 1000 条或 2 MiB 限制");
                    outputs.add(map("nodeId", id, "filename", node.config.get("filename"), "contentType",
                        node.config.get("format").equals("CSV") ? "text/csv; charset=UTF-8" : "application/json; charset=UTF-8", "bytes", bytes));
                }
                Map<String, Object> immutableStep = object(freeze(stepResult));
                steps.add(immutableStep);
                if (stepListener != null) stepListener.accept(immutableStep);
            } catch (CancellationException error) { throw error; }
            catch (IllegalArgumentException error) { throw new ExecutionException(id, error.getMessage()); }
        }
        check(stop, preview ? previewNodeId : "");
        return new Result(steps, outputs, totalInputRows, preview ? data.get(previewNodeId).rows.size() : totalOutputRows);
    }

    private static Plan plan(Map<String, Object> raw) {
        Map<String, Object> graph = object(copy(raw));
        List<?> nodes = list(graph.get("nodes")), edges = list(graph.get("connections"));
        if (nodes.size() > MAX_NODES || edges.size() > MAX_EDGES) throw invalid("流程最多 32 个节点、96 条连线");
        if (nodes.isEmpty()) throw invalid("请先添加输入、处理及输出节点");
        Map<String, Node> byId = new LinkedHashMap<>();
        Map<String, List<String>> parents = new LinkedHashMap<>(), children = new LinkedHashMap<>();
        for (Object item : nodes) {
            Map<String, Object> value = object(item);
            String id = text(value, "id"), type = text(value, "type"), name = text(value, "name");
            if (id.length() > 80 || name.length() > 128) throw invalid("节点标识或名称过长");
            if (!TYPES.contains(type)) throw new ExecutionException(id, "不支持的节点类型：" + type);
            if (byId.putIfAbsent(id, new Node(id, name, type, object(value.get("config")))) != null) throw new ExecutionException(id, "节点标识重复");
            parents.put(id, new ArrayList<>()); children.put(id, new ArrayList<>());
        }
        Set<String> edgeIds = new HashSet<>(), pairs = new HashSet<>();
        for (Object item : edges) {
            Map<String, Object> edge = object(item);
            String id = text(edge, "id"), source = text(edge, "sourceId"), target = text(edge, "targetId");
            if (!edgeIds.add(id) || !pairs.add(source + "\u0000" + target)) throw invalid("连线标识或连接关系重复");
            if (!byId.containsKey(source) || !byId.containsKey(target)) throw invalid("连线引用了不存在的节点");
            if (source.equals(target)) throw new ExecutionException(source, "节点不能连接自身");
            parents.get(target).add(source); children.get(source).add(target);
        }
        Map<String, Integer> degrees = new LinkedHashMap<>();
        ArrayDeque<String> ready = new ArrayDeque<>();
        for (Node node : byId.values()) {
            int degree = parents.get(node.id).size(); degrees.put(node.id, degree);
            if (degree == 0) ready.add(node.id);
            if (input(node) && degree != 0) throw new ExecutionException(node.id, "输入节点不能有上游");
            if (!input(node) && !node.type.equals("UNION") && degree > 1) throw new ExecutionException(node.id, "多个上游必须先通过合并节点汇总");
            if (node.type.equals("OUTPUT") && !children.get(node.id).isEmpty()) throw new ExecutionException(node.id, "输出节点不能连接下游");
        }
        List<String> order = new ArrayList<>();
        while (!ready.isEmpty()) {
            String id = ready.remove(); order.add(id);
            for (String child : children.get(id)) if (degrees.compute(child, (k, v) -> v - 1) == 0) ready.add(child);
        }
        if (order.size() != byId.size()) throw invalid("流程包含循环连线");
        return new Plan(byId, order, parents, children, edges.size());
    }

    private static void complete(Plan plan) {
        Set<String> inputs = new HashSet<>(); int outputs = 0;
        for (Node node : plan.nodes.values()) {
            if (input(node)) inputs.add(node.id);
            else if (plan.parents.get(node.id).size() < (node.type.equals("UNION") ? 2 : 1)) throw new ExecutionException(node.id, node.type.equals("UNION") ? "合并节点至少连接两个上游" : "节点尚未连接上游");
            if (node.type.equals("OUTPUT")) outputs++;
            else if (plan.children.get(node.id).isEmpty()) throw new ExecutionException(node.id, "该分支尚未连接输出节点");
        }
        if (inputs.isEmpty() || outputs == 0) throw invalid("流程必须包含输入和输出节点");
        if (inputs.size() > 1) for (Node node : plan.nodes.values()) if (node.type.equals("OUTPUT") && !ancestors(plan, node.id).containsAll(inputs))
            throw new ExecutionException(node.id, "多个输入必须通过合并节点汇总后输出");
    }

    private static Set<String> ancestors(Plan plan, String id) {
        Set<String> scope = new LinkedHashSet<>(); ArrayDeque<String> pending = new ArrayDeque<>(); pending.add(id);
        while (!pending.isEmpty()) { String current = pending.remove(); if (scope.add(current)) pending.addAll(plan.parents.get(current)); }
        return scope;
    }

    private static void config(Node node) {
        Map<String, Object> c = node.config;
        switch (node.type) {
            case "CSV_INPUT" -> { keys(c, "content", "delimiter", "header"); content(c); delimiter(c); bool(c, "header", true); }
            case "JSON_INPUT" -> { keys(c, "content"); content(c); }
            case "FIELD_MAP" -> {
                keys(c, "fields", "keepUnmapped"); bool(c, "keepUnmapped", false);
                Set<String> targets = new HashSet<>();
                for (Object f : fieldList(c)) { Map<String, Object> field = object(f); keys(field, "source", "target", "trim", "defaultValue"); column(text(field, "source")); String target = column(text(field, "target"));
                    if (!targets.add(target)) throw invalid("目标字段重复"); bool(field, "trim", false);
                    if (field.containsKey("defaultValue") && !(field.get("defaultValue") instanceof String)) throw invalid("默认值必须为文本"); }
            }
            case "FILTER" -> {
                keys(c, "field", "operator", "value", "valueType"); column(text(c, "field"));
                String op = choice(c, "operator", "EQ", "NE", "IS_NULL", "IS_NOT_NULL", "GT", "GE", "LT", "LE", "CONTAINS");
                String type = choice(c, "valueType", "STRING", "NUMBER");
                if (!op.equals("IS_NULL") && !op.equals("IS_NOT_NULL")) { if (!c.containsKey("value")) throw invalid("筛选比较值不能为空"); comparable(c.get("value"), type); }
                if (op.equals("CONTAINS") && !type.equals("STRING")) throw invalid("包含匹配仅支持文本类型");
            }
            case "SORT" -> { keys(c, "fields"); Set<String> fields = new HashSet<>(); for (Object f : fieldList(c)) { Map<String, Object> field = object(f); keys(field, "field", "direction", "valueType"); if (!fields.add(column(text(field, "field")))) throw invalid("排序字段重复"); choice(field, "direction", "ASC", "DESC"); choice(field, "valueType", "STRING", "NUMBER"); } }
            case "DEDUP" -> { keys(c, "fields"); Set<String> fields = new HashSet<>(); for (Object f : fieldList(c)) if (!(f instanceof String name) || !fields.add(column(name))) throw invalid("去重字段无效或重复"); }
            case "LOOKUP" -> {
                keys(c, "rows", "matches", "returns", "missing", "multiple"); if (list(c.get("rows")).size() > MAX_ROWS) throw invalid("快照最多 1000 条");
                for (Object row : list(c.get("rows"))) object(row);
                List<?> matches = boundedFields(c.get("matches")), returns = boundedFields(c.get("returns")); Set<String> names = new HashSet<>();
                for (Object m : matches) { Map<String, Object> match = object(m); keys(match, "inputField", "lookupField"); column(text(match, "inputField")); column(text(match, "lookupField")); }
                for (Object r : returns) { Map<String, Object> field = object(r); keys(field, "lookupField", "outputField"); column(text(field, "lookupField")); if (!names.add(column(text(field, "outputField")))) throw invalid("查表输出字段重复"); }
                choice(c, "missing", "KEEP", "DROP", "FAIL"); choice(c, "multiple", "FAIL", "FIRST", "LAST");
            }
            case "UNION" -> keys(c);
            case "OUTPUT" -> { keys(c, "format", "filename"); choice(c, "format", "CSV", "JSON"); String filename = text(c, "filename");
                if (filename.length() > 120 || filename.equals(".") || filename.equals("..") || filename.chars().anyMatch(ch -> Character.isISOControl(ch) || "/\\:".indexOf(ch) >= 0)) throw invalid("输出文件名不能包含路径或控制字符"); }
            default -> throw invalid("未知节点类型");
        }
    }

    private static Data readInput(Node node, BooleanSupplier stop) {
        String content = content(node.config); List<Map<String, Object>> rows = new ArrayList<>(); List<String> fields = new ArrayList<>();
        if (node.type.equals("JSON_INPUT")) {
            Object parsed = json(content);
            List<?> values = parsed instanceof Map<?, ?> ? List.of(parsed) : list(parsed);
            if (values.size() > MAX_ROWS) throw invalid("输入最多 1000 条");
            for (Object value : values) { check(stop, node.id); Map<String, Object> row = object(copy(value)); addFields(fields, row.keySet()); rows.add(row); }
        } else {
            boolean header = bool(node.config, "header", true);
            String normalized = content.startsWith("\uFEFF") ? content.substring(1) : content;
            CSVFormat format = CSVFormat.RFC4180.builder().setDelimiter(delimiter(node.config)).setIgnoreEmptyLines(true).setLenientEof(false).setTrailingData(false).get();
            try (CSVParser parser = CSVParser.parse(normalized, format)) {
                boolean first = true;
                for (CSVRecord record : parser) {
                    check(stop, node.id);
                    if (record.size() > MAX_FIELDS) throw invalid("输入最多 64 个字段");
                    if (first) {
                        for (int i = 0; i < record.size(); i++) { String name = header ? column(record.get(i)) : "column" + (i + 1); if (fields.contains(name)) throw invalid("CSV 表头字段重复"); fields.add(name); }
                        first = false; if (header) continue;
                    }
                    if (record.size() != fields.size()) throw invalid("CSV 行字段数与表头不一致，行号：" + record.getRecordNumber());
                    if (rows.size() >= MAX_ROWS) throw invalid("输入最多 1000 条");
                    Map<String, Object> row = new LinkedHashMap<>(); for (int i = 0; i < fields.size(); i++) row.put(fields.get(i), record.get(i)); rows.add(row);
                }
            } catch (IOException | java.io.UncheckedIOException error) { throw invalid("CSV 格式错误"); }
        }
        bound(rows); return new Data(rows, fields);
    }

    private static Data process(Node node, Data input, BooleanSupplier stop) {
        Map<String, Object> c = node.config; List<Map<String, Object>> rows = new ArrayList<>(); List<String> fields = new ArrayList<>(input.fields);
        switch (node.type) {
            case "OUTPUT" -> { return input; }
            case "FIELD_MAP" -> {
                List<?> mappings = fieldList(c); boolean keep = bool(c, "keepUnmapped", false);
                fields = keep ? new ArrayList<>(input.fields) : new ArrayList<>(); List<Map<String, Object>> rules = new ArrayList<>();
                for (int i = 0; i < mappings.size(); i++) { Map<String, Object> f = object(mappings.get(i)); addFields(fields, List.of(text(f, "target")));
                    rules.add(map("op", "get", "path", "/source/" + pointer(text(f, "source")), "output", "mapped" + i, "type", bool(f, "trim", false) ? "STRING" : "VALUE", "trim", bool(f, "trim", false) ? "BOTH" : "NONE", "missing", "NULL")); }
                JsonRecordTransform transform = new JsonRecordTransform(rules, CODEC);
                for (Map<String, Object> row : input.rows) { check(stop, node.id); Map<String, Object> transformed = object(transform.apply(map("source", row)).value()); Map<String, Object> out = keep ? new LinkedHashMap<>(row) : new LinkedHashMap<>();
                    for (int i = 0; i < mappings.size(); i++) { Map<String, Object> f = object(mappings.get(i)); Object value = transformed.get("mapped" + i); out.put(text(f, "target"), value == null && f.containsKey("defaultValue") ? f.get("defaultValue") : value); } rows.add(out); }
            }
            case "FILTER" -> {
                String field = text(c, "field"), op = text(c, "operator"), type = text(c, "valueType"); requireField(input, field);
                boolean nullPredicate = op.equals("IS_NULL") || op.equals("IS_NOT_NULL");
                Object expected = nullPredicate ? null : comparable(c.get("value"), type);
                for (Map<String, Object> row : input.rows) { check(stop, node.id); Object value = nullPredicate ? row.get(field) : comparable(row.get(field), type); boolean hit;
                    if (op.equals("IS_NULL")) hit = value == null;
                    else if (op.equals("IS_NOT_NULL")) hit = value != null;
                    else if (op.equals("EQ")) hit = equal(value, expected);
                    else if (op.equals("NE")) hit = !equal(value, expected);
                    else if (op.equals("CONTAINS")) hit = value != null && expected != null && value.toString().contains(expected.toString());
                    else { int compared = value == null || expected == null ? 0 : compare(value, expected); hit = value != null && expected != null && switch (op) { case "GT" -> compared > 0; case "GE" -> compared >= 0; case "LT" -> compared < 0; default -> compared <= 0; }; }
                    if (hit) rows.add(row);
                }
            }
            case "SORT" -> {
                List<?> orders = fieldList(c); for (Object item : orders) requireField(input, text(object(item), "field"));
                for (Map<String, Object> row : input.rows) { check(stop, node.id); for (Object item : orders) { Map<String, Object> order = object(item); comparable(row.get(text(order, "field")), text(order, "valueType")); } rows.add(row); }
                rows.sort((a, b) -> { check(stop, node.id); for (Object item : orders) { Map<String, Object> order = object(item); String field = text(order, "field"), type = text(order, "valueType"); Object left = comparable(a.get(field), type), right = comparable(b.get(field), type); if (left == null || right == null) { if (left != right) return left == null ? 1 : -1; continue; } int compared = compare(left, right); if (compared != 0) return text(order, "direction").equals("DESC") ? -compared : compared; } return 0; });
            }
            case "DEDUP" -> {
                List<?> names = fieldList(c); for (Object name : names) requireField(input, (String) name); Set<List<Object>> seen = new HashSet<>();
                for (Map<String, Object> row : input.rows) { check(stop, node.id); List<Object> key = new ArrayList<>(); for (Object name : names) { Object value = row.get(name); if (value instanceof Number) value = number(value).stripTrailingZeros(); else if (value instanceof Map<?, ?> || value instanceof List<?>) throw invalid("去重字段应为标量值"); key.add(value); } if (seen.add(key)) rows.add(row); }
            }
            case "LOOKUP" -> { return lookup(node, input, stop); }
            default -> throw invalid("未知处理节点");
        }
        return new Data(rows, fields);
    }

    private static Data lookup(Node node, Data input, BooleanSupplier stop) {
        Map<String, Object> c = node.config; List<?> matches = list(c.get("matches")), returns = list(c.get("returns"));
        List<LookupSnapshot.Match> compiledMatches = new ArrayList<>(); List<LookupSnapshot.ReturnField> compiledReturns = new ArrayList<>();
        for (int i = 0; i < matches.size(); i++) { requireField(input, text(object(matches.get(i)), "inputField")); compiledMatches.add(new LookupSnapshot.Match("/keys/key" + i, "key" + i, LookupSnapshot.ValueType.STRING, LookupSnapshot.Operator.EQ)); }
        for (int i = 0; i < returns.size(); i++) compiledReturns.add(new LookupSnapshot.ReturnField("return" + i, "returned" + i, null));
        List<Map<String, Object>> snapshot = new ArrayList<>();
        for (Object item : list(c.get("rows"))) { check(stop, node.id); Map<String, Object> row = object(item), mapped = new LinkedHashMap<>();
            for (int i = 0; i < matches.size(); i++) mapped.put("key" + i, comparable(row.get(text(object(matches.get(i)), "lookupField")), "STRING"));
            for (int i = 0; i < returns.size(); i++) { String key = text(object(returns.get(i)), "lookupField"); if (!row.containsKey(key)) throw invalid("快照缺少返回字段：" + key); mapped.put("return" + i, row.get(key)); } snapshot.add(mapped); }
        LookupSnapshot lookup = new LookupSnapshot(snapshot, compiledMatches, compiledReturns, LookupSnapshot.MissingMatch.valueOf(text(c, "missing")), LookupSnapshot.MultipleMatches.valueOf(text(c, "multiple")));
        List<Map<String, Object>> rows = new ArrayList<>(); List<String> fields = new ArrayList<>(input.fields);
        for (Object item : returns) addFields(fields, List.of(text(object(item), "outputField")));
        for (Map<String, Object> row : input.rows) { check(stop, node.id); Map<String, Object> keys = new LinkedHashMap<>(); for (int i = 0; i < matches.size(); i++) keys.put("key" + i, comparable(row.get(text(object(matches.get(i)), "inputField")), "STRING"));
            LookupSnapshot.Result result = lookup.apply(map("keys", keys)); if (result.empty()) continue; Map<String, Object> enriched = new LinkedHashMap<>(row), found = object(result.value());
            for (int i = 0; i < returns.size(); i++) enriched.put(text(object(returns.get(i)), "outputField"), found.get("returned" + i)); rows.add(enriched); }
        return new Data(rows, fields);
    }

    private static Data union(List<Data> incoming, BooleanSupplier stop, String id) {
        if (incoming.size() < 2) throw invalid("合并节点至少连接两个上游"); List<Map<String, Object>> rows = new ArrayList<>(); List<String> fields = new ArrayList<>();
        for (Data data : incoming) { addFields(fields, data.fields); for (Map<String, Object> row : data.rows) { check(stop, id); if (rows.size() >= MAX_ROWS) throw invalid("合并结果最多 1000 条"); rows.add(row); } }
        // Normalize the union schema so absent columns are explicit null values.
        List<Map<String, Object>> normalized = new ArrayList<>(); for (Map<String, Object> row : rows) { check(stop, id); Map<String, Object> mapped = new LinkedHashMap<>(); for (String field : fields) mapped.put(field, row.get(field)); normalized.add(mapped); }
        return new Data(normalized, fields);
    }

    private static byte[] output(Node node, Data data, BooleanSupplier stop) {
        if (text(node.config, "format").equals("JSON")) return encodeJson(data.rows).getBytes(StandardCharsets.UTF_8);
        if (data.fields.isEmpty()) return new byte[0];
        // Commons CSV quotes cells; the existing bounded encoder owns UTF-8/newlines/byte budget.
        List<DelimitedBatchEncoder.Row> encoded = new ArrayList<>();
        List<String> header = new ArrayList<>(); for (String field : data.fields) header.add(CSV_OUTPUT.format(field));
        encoded.add(new DelimitedBatchEncoder.Row(header, 0));
        for (Map<String, Object> row : data.rows) { check(stop, node.id); List<String> cells = new ArrayList<>(); for (String field : data.fields) { Object value = row.get(field); cells.add(CSV_OUTPUT.format(value == null ? "" : value instanceof Map<?, ?> || value instanceof List<?> ? encodeJson(value) : value.toString())); } encoded.add(new DelimitedBatchEncoder.Row(cells, 0)); }
        DelimitedBatchEncoder.Options options = new DelimitedBatchEncoder.Options(data.fields, "2C", false, 0, DelimitedBatchEncoder.CountBasis.DATA_RECORDS, 0, "\r\n");
        return new DelimitedBatchEncoder().encode(encoded, options, MAX_OUTPUT_BYTES).get(0).content();
    }

    private static void check(BooleanSupplier stop, String id) { if (Thread.currentThread().isInterrupted() || stop.getAsBoolean()) throw new CancellationException("流程已取消，节点：" + id); }
    private static boolean input(Node node) { return node.type.equals("CSV_INPUT") || node.type.equals("JSON_INPUT"); }
    private static void requireField(Data input, String name) { if (!input.fields.contains(name) && (!input.fields.isEmpty() || !input.rows.isEmpty())) throw invalid("上游不存在字段：" + name); }
    private static void addFields(List<String> fields, Iterable<String> names) { for (String name : names) { column(name); if (!fields.contains(name)) fields.add(name); if (fields.size() > MAX_FIELDS) throw invalid("节点最多 64 个字段"); } }
    private static String pointer(String value) { return value.replace("~", "~0").replace("/", "~1"); }
    private static Object comparable(Object value, String type) { if (value == null) return null; if (value instanceof Map<?, ?> || value instanceof List<?>) throw invalid("比较字段应为标量值"); if (type.equals("NUMBER")) return number(value); if (value instanceof Number) { BigDecimal decimal = number(value).stripTrailingZeros(); long width = (long) decimal.precision() + Math.max(0L, -(long) decimal.scale()); return width <= 128 && decimal.scale() <= 128 ? decimal.toPlainString() : decimal.toString(); } return value.toString(); }
    private static BigDecimal number(Object value) { String text = value.toString(); if (text.length() > 128 || !text.matches("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?")) throw invalid("数值比较需要有效十进制数值"); try { BigDecimal number = new BigDecimal(text); if (Math.abs((long) number.scale()) > 10000) throw invalid("数值精度超限"); return number; } catch (NumberFormatException error) { throw invalid("无效数值"); } }
    private static boolean equal(Object a, Object b) { return a == null || b == null ? a == b : compare(a, b) == 0; }
    @SuppressWarnings({"rawtypes", "unchecked"}) private static int compare(Object a, Object b) { return ((Comparable) a).compareTo(b); }
    private static String content(Map<String, Object> c) { Object value = c.get("content"); if (!(value instanceof String text) || utf8(text) > MAX_INPUT_BYTES) throw invalid("输入内容必须为最多 1 MiB 的文本"); return text; }
    private static char delimiter(Map<String, Object> c) { Object value = c.getOrDefault("delimiter", ","); if (!(value instanceof String text) || text.length() != 1 || "\r\n\"\u0000".indexOf(text.charAt(0)) >= 0) throw invalid("CSV 分隔符必须为一个有效字符"); return text.charAt(0); }
    private static String column(String value) { if (value == null || value.isBlank() || value.length() > 128 || value.chars().anyMatch(Character::isISOControl)) throw invalid("字段名称无效"); return value; }
    private static String text(Map<String, Object> value, String key) { Object item = value.get(key); if (!(item instanceof String text) || text.isBlank()) throw invalid("缺少配置：" + key); return text; }
    private static boolean bool(Map<String, Object> value, String key, boolean fallback) { Object item = value.getOrDefault(key, fallback); if (!(item instanceof Boolean result)) throw invalid("配置必须为布尔值：" + key); return result; }
    private static String choice(Map<String, Object> value, String key, String... allowed) { String text = text(value, key); if (!Set.of(allowed).contains(text)) throw invalid("不支持的配置：" + key); return text; }
    private static void keys(Map<String, Object> value, String... allowed) { Set<String> keys = Set.of(allowed); for (String key : value.keySet()) if (!keys.contains(key)) throw invalid("不支持的节点配置：" + key); }
    private static List<?> fieldList(Map<String, Object> value) { return boundedFields(value.get("fields")); }
    private static List<?> boundedFields(Object value) { List<?> fields = list(value); if (fields.isEmpty() || fields.size() > MAX_FIELDS) throw invalid("请配置 1–64 个字段"); return fields; }
    @SuppressWarnings("unchecked") private static Map<String, Object> object(Object value) { if (!(value instanceof Map<?, ?>)) throw invalid("配置或数据记录必须为对象"); return (Map<String, Object>) value; }
    private static List<?> list(Object value) { if (!(value instanceof List<?> list)) throw invalid("配置或输入数据必须为数组"); return list; }
    private static int utf8(String text) { return text.getBytes(StandardCharsets.UTF_8).length; }
    private static Object json(String text) { try { return JSON.readValue(text, Object.class); } catch (IOException error) { throw invalid("JSON 格式错误"); } }
    private static String encodeJson(Object value) { try { return JSON.writeValueAsString(value); } catch (IOException error) { throw invalid("数据无法编码为 JSON"); } }
    private static void bound(List<Map<String, Object>> rows) { if (rows.size() > MAX_ROWS) throw invalid("节点输出最多 1000 条"); copy(rows); }
    private static Object copy(Object value) { return copy(value, 0, new int[2], Collections.newSetFromMap(new IdentityHashMap<>())); }
    private static Object copy(Object value, int depth, int[] budget, Set<Object> ancestors) {
        if (depth > 32 || ++budget[0] > 100000) throw invalid("数据结构超过深度或数量限制");
        budget[1] += value instanceof String text ? utf8(text) : 8;
        if (budget[1] > MAX_OUTPUT_BYTES) throw invalid("数据内容超过 2 MiB 限制");
        if (value == null || value instanceof String || value instanceof Boolean) return value;
        if (value instanceof BigDecimal || value instanceof BigInteger || value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long || value instanceof Double || value instanceof Float) { number(value); return value; }
        if (!ancestors.add(value)) throw invalid("数据结构存在循环引用");
        try {
            if (value instanceof Map<?, ?> map) { Map<String, Object> result = new LinkedHashMap<>(); for (var entry : map.entrySet()) { if (!(entry.getKey() instanceof String key)) throw invalid("字段名必须为文本"); budget[1] += utf8(key); result.put(key, copy(entry.getValue(), depth + 1, budget, ancestors)); } return result; }
            if (value instanceof List<?> list) { List<Object> result = new ArrayList<>(); for (Object item : list) result.add(copy(item, depth + 1, budget, ancestors)); return result; }
            throw invalid("不支持的数据值类型");
        } finally { ancestors.remove(value); }
    }
    private static Object freeze(Object value) {
        if (value instanceof Map<?, ?> map) { Map<String, Object> result = new LinkedHashMap<>(); for (var entry : map.entrySet()) result.put((String) entry.getKey(), freeze(entry.getValue())); return Collections.unmodifiableMap(result); }
        if (value instanceof List<?> list) { List<Object> result = new ArrayList<>(); for (Object item : list) result.add(freeze(item)); return Collections.unmodifiableList(result); }
        return value;
    }
    private static Map<String, Object> error(String id, String message) { return map("nodeId", id, "message", message); }
    private static Map<String, Object> map(Object... entries) { Map<String, Object> result = new LinkedHashMap<>(); for (int i = 0; i < entries.length; i += 2) result.put((String) entries[i], entries[i + 1]); return result; }
    private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }
}
