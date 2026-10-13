package com.hm.manage.service.governance;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.junit.jupiter.api.Test;

class DataGovernanceLocalEngineTest {
    final DataGovernanceLocalEngine engine = new DataGovernanceLocalEngine();
    static Map<String, Object> m(Object... values) { Map<String, Object> result = new LinkedHashMap<>(); for (int i = 0; i < values.length; i += 2) result.put((String) values[i], values[i + 1]); return result; }
    static Map<String, Object> node(String id, String type, Map<String, Object> config) { return m("id", id, "name", id, "type", type, "position", m("x", 1, "y", 2), "config", config); }
    static Map<String, Object> source(String json) { return node("input", "JSON_INPUT", m("content", json)); }
    static Map<String, Object> output(String format) { return node("output", "OUTPUT", m("format", format, "filename", "result." + format.toLowerCase())); }
    static Map<String, Object> edge(String source, String target) { return m("id", source + "-" + target, "sourceId", source, "targetId", target); }
    @SafeVarargs static Map<String, Object> chain(Map<String, Object>... nodes) { List<Map<String, Object>> edges = new ArrayList<>(); for (int i = 1; i < nodes.length; i++) edges.add(edge((String) nodes[i - 1].get("id"), (String) nodes[i].get("id"))); return m("nodes", List.of(nodes), "connections", edges); }
    DataGovernanceLocalEngine.Result run(Map<String, Object> graph) { return engine.execute(graph, null, 1, () -> false); }
    static String bytes(DataGovernanceLocalEngine.Result result) { return new String((byte[]) result.outputs().get(0).get("bytes"), StandardCharsets.UTF_8); }
    @SuppressWarnings("unchecked") static List<Map<String, Object>> rows(Map<String, Object> step) { return (List<Map<String, Object>>) step.get("sampleRows"); }
    @SuppressWarnings("unchecked") static Map<String, Object> config(Map<String, Object> node) { return (Map<String, Object>) node.get("config"); }

    @Test void csvMapFilterSortDedupLookupAndOutputUseFullRowsBeyondSample() {
        var input = node("input", "CSV_INPUT", m("content", "id,name,score\n2, 张三 ,20\n1, Alice ,10\n2, 张三 ,20\n3, Bob ,30\n", "delimiter", ",", "header", true));
        var mapping = node("map", "FIELD_MAP", m("fields", List.of(m("source", "id", "target", "id"), m("source", "name", "target", "name", "trim", true), m("source", "score", "target", "score"), m("source", "missing", "target", "origin", "defaultValue", "local")), "keepUnmapped", false));
        var filter = node("filter", "FILTER", m("field", "score", "operator", "GE", "value", "20", "valueType", "NUMBER"));
        var sort = node("sort", "SORT", m("fields", List.of(m("field", "score", "direction", "DESC", "valueType", "NUMBER"))));
        var dedup = node("dedup", "DEDUP", m("fields", List.of("id")));
        var lookup = node("lookup", "LOOKUP", m("rows", List.of(m("code", 2, "group", "B"), m("code", 3, "group", "C")), "matches", List.of(m("inputField", "id", "lookupField", "code")), "returns", List.of(m("lookupField", "group", "outputField", "group")), "missing", "FAIL", "multiple", "FAIL"));
        var graph = chain(input, mapping, filter, sort, dedup, lookup, output("CSV"));
        assertEquals(true, engine.validate(graph).get("valid"));
        var result = run(graph);
        assertEquals(4, result.inputRows()); assertEquals(2, result.outputRows());
        assertEquals("id,name,score,origin,group\r\n3,Bob,30,local,C\r\n2,张三,20,local,B\r\n", bytes(result));
        assertEquals(3, result.steps().get(2).get("outputRows"));
        assertEquals(1, rows(result.steps().get(2)).size()); assertEquals(true, result.steps().get(2).get("sampleTruncated"));
    }

    @Test void csvParserAndWriterRoundTripCommasQuotesAndEmbeddedNewlines() throws Exception {
        String csv = "name,message\r\nAlice,\"hello, \"\"world\"\"\nnext\"\r\n张三,尾部\r\n";
        var result = run(chain(node("input", "CSV_INPUT", m("content", "\uFEFF" + csv, "header", true)), output("CSV")));
        try (CSVParser parsed = CSVParser.parse(bytes(result), CSVFormat.RFC4180)) {
            var records = parsed.getRecords(); assertEquals(3, records.size()); assertEquals("hello, \"world\"\nnext", records.get(1).get(1));
        }
    }

    @Test void headerlessCsvGeneratesColumnsWithoutDiscardingFirstRow() {
        var result = run(chain(node("input", "CSV_INPUT", m("content", "a;b\nc;d", "delimiter", ";", "header", false)), output("JSON")));
        assertEquals(2, result.outputRows()); assertEquals("[{\"column1\":\"a\",\"column2\":\"b\"},{\"column1\":\"c\",\"column2\":\"d\"}]", bytes(result));
    }

    @Test void simultaneousFieldSwapReadsOriginalValuesAndPreservesInputGraph() {
        var input = source("[{\"a\":\" left \",\"b\":\"right\",\"other\":true}]");
        var mapping = node("map", "FIELD_MAP", m("fields", List.of(m("source", "a", "target", "b", "trim", true), m("source", "b", "target", "a")), "keepUnmapped", true));
        var graph = chain(input, mapping, output("JSON")); String before = graph.toString();
        assertEquals("[{\"a\":\"right\",\"b\":\"left\",\"other\":true}]", bytes(run(graph)));
        assertEquals(before, graph.toString());
    }

    @Test void unionNormalizesSchemasAndRejectsIndependentMultipleInputPipelines() {
        var a = source("[{\"a\":1}]"); var b = node("input2", "JSON_INPUT", m("content", "[{\"b\":2}]")); var union = node("union", "UNION", m()); var out = output("JSON");
        var graph = m("nodes", List.of(a, b, union, out), "connections", List.of(edge("input", "union"), edge("input2", "union"), edge("union", "output")));
        assertEquals(true, engine.validate(graph).get("valid"));
        assertEquals("[{\"a\":1,\"b\":null},{\"a\":null,\"b\":2}]", bytes(run(graph)));
        var out2 = node("output2", "OUTPUT", m("format", "JSON", "filename", "second.json"));
        assertEquals(false, engine.validate(m("nodes", List.of(a, b, out, out2), "connections", List.of(edge("input", "output"), edge("input2", "output2")))).get("valid"));
        assertEquals(false, engine.validate(m("nodes", List.of(a, b, out), "connections", List.of(edge("input", "output"), edge("input2", "output")))).get("valid"));
    }

    @Test void previewEvaluatesOnlyAncestorsAndProducesNoArtifacts() {
        var in = source("[{\"x\":1},{\"x\":2}]"); var filter = node("filter", "FILTER", m("field", "x", "operator", "GT", "value", 0, "valueType", "NUMBER"));
        var invalidOutput = node("output", "OUTPUT", m("format", "CSV", "filename", "../bad"));
        var irrelevant = node("other", "JSON_INPUT", m("content", "malformed"));
        var graph = m("nodes", List.of(in, filter, invalidOutput, irrelevant), "connections", List.of(edge("input", "filter"), edge("filter", "output")));
        var preview = engine.execute(graph, "filter", 1, () -> false);
        assertEquals(2, preview.steps().size()); assertEquals(2, preview.outputRows()); assertEquals(0, preview.outputs().size());
        assertEquals(1, rows(preview.steps().get(1)).size()); assertEquals(false, engine.validate(graph).get("valid"));
        assertEquals(false, engine.validate(chain(in, filter)).get("valid"));
    }

    @Test void invalidGraphsAndUnsupportedExecutorsAreRejected() {
        Map<String, Object> a = source("[]"), b = node("b", "DEDUP", m("fields", List.of("x"))), out = output("JSON");
        for (Map<String, Object> graph : List.of(m("nodes", List.of(), "connections", List.of()), chain(a, a), m("nodes", List.of(a, b, out), "connections", List.of(edge("input", "b"), edge("b", "output"), edge("b", "b"))), m("nodes", List.of(a, out), "connections", List.of(edge("input", "missing"))), chain(source("[]"), node("script", "SCRIPT", m("code", "danger")), out))) assertEquals(false, engine.validate(graph).get("valid"));
        assertEquals(false, engine.validate(chain(source("[]"), node("bad", "OUTPUT", m("format", "JSON", "filename", "x.json", "url", "http://host")))).get("valid"));
    }

    @Test void trueCycleAndDuplicateEdgesCannotRun() {
        Map<String, Object> input = source("[]"), a = node("a", "UNION", m()), b = node("b", "DEDUP", m("fields", List.of("x"))), out = output("JSON");
        var graph = m("nodes", List.of(input, a, b, out), "connections", List.of(edge("input", "a"), edge("a", "b"), edge("b", "a"), edge("b", "output")));
        assertEquals(false, engine.validate(graph).get("valid")); assertThrows(IllegalArgumentException.class, () -> run(graph));
        assertEquals(false, engine.validate(m("nodes", List.of(input, out), "connections", List.of(edge("input", "output"), edge("input", "output")))).get("valid"));
    }

    @Test void malformedInputsAndDuplicateHeadersFailWithNodeContext() {
        for (String content : List.of("[{\"a\":1,\"a\":2}]", "{\"a\":1} trailing", "[1,2]")) {
            var graph = chain(source(content), output("JSON")); assertEquals(false, engine.validate(graph).get("valid"));
            assertEquals("input", assertThrows(DataGovernanceLocalEngine.ExecutionException.class, () -> run(graph)).nodeId());
        }
        for (String csv : List.of("x,x\na,b", "x,y\nshort", "x\n\"unterminated")) assertEquals(false, engine.validate(chain(node("input", "CSV_INPUT", m("content", csv)), output("CSV"))).get("valid"));
    }

    @Test void boundedInputsRejectRowsBytesAndGraphLimits() {
        assertEquals(false, engine.validate(chain(source("[" + "{\"x\":1},".repeat(1000) + "{\"x\":1}]"), output("JSON"))).get("valid"));
        assertEquals(false, engine.validate(chain(source("汉".repeat(350000)), output("JSON"))).get("valid"));
        List<Map<String, Object>> nodes = new ArrayList<>(); for (int i = 0; i < 33; i++) nodes.add(node("n" + i, "JSON_INPUT", m("content", "[]")));
        assertEquals(false, engine.validate(m("nodes", nodes, "connections", List.of())).get("valid"));
        Map<String, Object> cyclic = m(); cyclic.put("nodes", cyclic); cyclic.put("connections", List.of()); assertEquals(false, engine.validate(cyclic).get("valid"));
    }

    @Test void aggregateInputAndBranchedOutputLimitsDoNotSilentlyTruncate() {
        String json = "[" + "{\"x\":1},".repeat(599) + "{\"x\":1}]";
        var first = source(json); var second = node("second-input", "JSON_INPUT", m("content", json)); var union = node("union", "UNION", m());
        var tooManyInputs = m("nodes", List.of(first, second, union, output("JSON")), "connections", List.of(edge("input", "union"), edge("second-input", "union"), edge("union", "output")));
        assertEquals(false, engine.validate(tooManyInputs).get("valid"));
        assertEquals("second-input", assertThrows(DataGovernanceLocalEngine.ExecutionException.class, () -> run(tooManyInputs)).nodeId());
        var secondOutput = node("second-output", "OUTPUT", m("format", "JSON", "filename", "second.json"));
        var tooManyOutputs = m("nodes", List.of(first, output("JSON"), secondOutput), "connections", List.of(edge("input", "output"), edge("input", "second-output")));
        assertEquals("second-output", assertThrows(DataGovernanceLocalEngine.ExecutionException.class, () -> run(tooManyOutputs)).nodeId());
    }

    @Test void emptyBatchIsSuccessWithHonestCountsAndKnownHeader() {
        var filter = node("filter", "FILTER", m("field", "x", "operator", "EQ", "value", "not-found", "valueType", "STRING"));
        var result = run(chain(node("input", "CSV_INPUT", m("content", "x,y\na,b\n")), filter, output("CSV")));
        assertEquals(1, result.inputRows()); assertEquals(0, result.outputRows()); assertEquals("x,y\r\n", bytes(result));
        assertEquals(List.of("x", "y"), result.steps().get(1).get("fields"));
        assertEquals("[]", bytes(run(chain(source("[]"), output("JSON")))));
    }

    @Test void nullPredicatesDoNotCoerceNonNullValuesToNumbers() {
        var filter = node("filter", "FILTER", m("field", "x", "operator", "IS_NOT_NULL", "valueType", "NUMBER"));
        assertEquals(1, run(chain(source("[{\"x\":\"text\"},{\"x\":null},{}]"), filter, output("JSON"))).outputRows());
        config(filter).put("operator", "IS_NULL"); assertEquals(2, run(chain(source("[{\"x\":\"text\"},{\"x\":null},{}]"), filter, output("JSON"))).outputRows());
    }

    @Test void numericSortNullsLastAndDedupAreDeterministic() {
        var sorted = node("sort", "SORT", m("fields", List.of(m("field", "x", "direction", "ASC", "valueType", "NUMBER"))));
        var dedup = node("dedup", "DEDUP", m("fields", List.of("x")));
        var result = run(chain(source("[{\"x\":10},{\"x\":1.0},{\"x\":null},{\"x\":1}]"), sorted, dedup, output("JSON")));
        assertEquals(3, result.outputRows()); assertEquals("[{\"x\":1.0},{\"x\":10},{\"x\":null}]", bytes(result));
        assertEquals("sort", assertThrows(DataGovernanceLocalEngine.ExecutionException.class, () -> run(chain(source("[{\"x\":\"NaN\"}]"), sorted, output("JSON")))).nodeId());
    }

    @Test void lookupMissingAndMultiplePoliciesUseCoreSemantics() {
        var lookup = node("lookup", "LOOKUP", m("rows", List.of(m("key", "a", "label", "first"), m("key", "a", "label", "last")), "matches", List.of(m("inputField", "key", "lookupField", "key")), "returns", List.of(m("lookupField", "label", "outputField", "label")), "missing", "KEEP", "multiple", "FIRST"));
        var graph = chain(source("[{\"key\":\"a\"},{\"key\":\"b\"}]"), lookup, output("JSON"));
        assertEquals("[{\"key\":\"a\",\"label\":\"first\"},{\"key\":\"b\",\"label\":null}]", bytes(run(graph)));
        config(lookup).put("multiple", "LAST"); config(lookup).put("missing", "DROP"); assertEquals("[{\"key\":\"a\",\"label\":\"last\"}]", bytes(run(graph)));
        config(lookup).put("missing", "FAIL"); assertEquals("lookup", assertThrows(DataGovernanceLocalEngine.ExecutionException.class, () -> run(graph)).nodeId());
        config(lookup).put("missing", "KEEP"); config(lookup).put("multiple", "FAIL"); assertEquals("lookup", assertThrows(DataGovernanceLocalEngine.ExecutionException.class, () -> run(graph)).nodeId());
    }

    @Test void cancellationIsCheckedInsideInputRowsAndAtNodeBoundaries() {
        AtomicInteger calls = new AtomicInteger(); var graph = chain(source("[" + "{\"x\":1},".repeat(50) + "{\"x\":1}]"), output("JSON"));
        var failure = assertThrows(CancellationException.class, () -> engine.execute(graph, null, 10, () -> calls.incrementAndGet() > 15)); assertTrue(failure.getMessage().contains("input")); assertTrue(calls.get() < 30);
        assertThrows(CancellationException.class, () -> engine.execute(graph, null, 10, () -> true));
    }

    @Test void listenerSnapshotsAreImmutableAndLaterFailurePublishesNoResult() {
        List<Map<String, Object>> events = new ArrayList<>(); var graph = chain(source("[{\"x\":1}]"), output("JSON"));
        engine.execute(graph, null, 5, () -> false, step -> { events.add(step); assertThrows(UnsupportedOperationException.class, () -> step.put("status", "changed")); assertThrows(UnsupportedOperationException.class, () -> rows(step).get(0).put("x", 2)); });
        assertEquals(2, events.size());
        var bad = node("bad", "FILTER", m("field", "x", "operator", "GT", "value", "not-a-number", "valueType", "NUMBER"));
        var second = node("second", "OUTPUT", m("format", "JSON", "filename", "second.json"));
        var failing = m("nodes", List.of(source("[{\"x\":1}]"), output("JSON"), bad, second), "connections", List.of(edge("input", "output"), edge("input", "bad"), edge("bad", "second")));
        assertEquals("bad", assertThrows(DataGovernanceLocalEngine.ExecutionException.class, () -> run(failing)).nodeId());
    }
}
