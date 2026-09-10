package com.hm.governance.nifi;

import static org.junit.jupiter.api.Assertions.*;
import com.alibaba.fastjson2.JSON;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import org.junit.jupiter.api.Test;

class JsonRecordTransformTest {
    private static final String RULES = """
        [{"op":"parse","input":"/payload","document":"doc"},
         {"op":"get","document":"doc","path":"/events/0/code","output":"code","type":"STRING","trim":"BOTH"},
         {"op":"replace","input":"/code","output":"code","find":"A.","replacement":"B$","mode":"ALL"},
         {"op":"broadcast","document":"doc","array":"/events","path":"/attrs/mappedCode","input":"/code","ifNotNull":true},
         {"op":"serialize","document":"doc","output":"payload"}]
        """;
    private TestRunner runner() { return configure(TestRunners.newTestRunner(JsonRecordTransform.class)); }
    private TestRunner configure(TestRunner runner) { runner.setProperty(JsonRecordTransform.OPERATIONS, RULES); return runner; }
    private String record(String code) { return JSON.toJSONString(Map.of("payload", JSON.toJSONString(Map.of("events", List.of(Map.of("code", code, "attrs", Map.of()), Map.of("code", "other", "attrs", Map.of())))), "lookupKeep", "retained")); }

    @Test void actualJsonPipelineBroadcastsCreatesLeavesAndRetainsUnrelatedFields() throws Exception {
        var runner = runner(); runner.enqueue(record(" A.01 ")); runner.run();
        runner.assertTransferCount(JsonRecordTransform.SUCCESS, 1); runner.assertTransferCount(JsonRecordTransform.FAILURE, 0);
        var file = runner.getFlowFilesForRelationship(JsonRecordTransform.SUCCESS).get(0);
        var row = JSON.parseObject(new String(file.toByteArray(), StandardCharsets.UTF_8));
        assertEquals("B$01", row.getString("code")); assertEquals("retained", row.getString("lookupKeep")); assertFalse(row.containsKey("doc"));
        var events = JSON.parseObject(row.getString("payload")).getJSONArray("events");
        assertEquals("B$01", events.getJSONObject(0).getJSONObject("attrs").getString("mappedCode"));
        assertEquals("B$01", events.getJSONObject(1).getJSONObject("attrs").getString("mappedCode"));
        assertEquals("other", events.getJSONObject(1).getString("code"));
        file.assertAttributeEquals("record.count", "1"); file.assertAttributeEquals("governance.transform.input.records", "1");
        file.assertAttributeEquals("governance.transform.operations.sha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(RULES.getBytes(StandardCharsets.UTF_8))));
    }
    @Test void objectArraysPreserveShapeAndEmptyBatchIsExplicit() {
        var runner = runner(); runner.enqueue("[" + record("A.1") + "," + record("A.2") + "]"); runner.run();
        var file = runner.getFlowFilesForRelationship(JsonRecordTransform.SUCCESS).get(0); file.assertAttributeEquals("record.count", "2");
        assertEquals(2, JSON.parseArray(new String(file.toByteArray(), StandardCharsets.UTF_8)).size());
        var empty = runner(); empty.enqueue("[]"); empty.run(); empty.assertTransferCount(JsonRecordTransform.EMPTY, 1); empty.assertTransferCount(JsonRecordTransform.SUCCESS, 0);
        empty.getFlowFilesForRelationship(JsonRecordTransform.EMPTY).get(0).assertContentEquals("[]");
    }
    @Test void laterRecordFailureKeepsOriginalEntireBatchAndDoesNotLeakValues() {
        String original = "[" + record("good") + "," + JSON.toJSONString(Map.of("payload", "private-invalid-json")) + "]";
        var runner = runner(); runner.enqueue(original); runner.run(); runner.assertTransferCount(JsonRecordTransform.SUCCESS, 0); runner.assertTransferCount(JsonRecordTransform.FAILURE, 1);
        var file = runner.getFlowFilesForRelationship(JsonRecordTransform.FAILURE).get(0); file.assertContentEquals(original);
        assertFalse(file.getAttribute("governance.error").contains("private-invalid-json"));
    }
    @Test void documentOuterIndexOutOfBoundsRejectsWholeBatch() {
        var runner = runner(); runner.setProperty(JsonRecordTransform.OPERATIONS, """
            [{"op":"parse","input":"/payload","document":"doc"},{"op":"broadcast","document":"doc","array":"/events","path":"/targets/$index/value","input":"/value","outerIndex":true},{"op":"serialize","document":"doc","output":"payload"}]
            """);
        String original = JSON.toJSONString(Map.of("payload", "{\"events\":[{\"targets\":[{}]},{\"targets\":[{}]}]}", "value", "new"));
        runner.enqueue(original); runner.run(); runner.assertTransferCount(JsonRecordTransform.FAILURE, 1); runner.assertTransferCount(JsonRecordTransform.SUCCESS, 0);
        runner.getFlowFilesForRelationship(JsonRecordTransform.FAILURE).get(0).assertContentEquals(original);
    }
    @Test void parserPreservesExactDecimalValuesAndTreatsExpressionTextLiterally() {
        var runner = runner(); runner.setProperty(JsonRecordTransform.OPERATIONS, "[{\"op\":\"get\",\"path\":\"/value\",\"output\":\"copied\"}]");
        String input = "{\"value\":9007199254740993.123456789012345678901,\"literal\":\"${secret}\"}";
        runner.enqueue(input); runner.run(); var file = runner.getFlowFilesForRelationship(JsonRecordTransform.SUCCESS).get(0);
        String output = new String(file.toByteArray(), StandardCharsets.UTF_8);
        assertTrue(output.contains("9007199254740993.123456789012345678901")); assertTrue(output.contains("${secret}"));
    }
    @Test void malformedUtf8AndDuplicateKeysInInputRulesOrDocumentFailClosed() {
        for (String input : List.of("{\"x\":1,\"x\":2}", "{'x':1}", "[1]", "{} trailing", JSON.toJSONString(Map.of("payload", "{\"events\":[],\"events\":[{}]}")))) {
            var runner = runner(); runner.enqueue(input); runner.run(); runner.assertTransferCount(JsonRecordTransform.FAILURE, 1); runner.assertTransferCount(JsonRecordTransform.SUCCESS, 0);
        }
        var utf8 = runner(); utf8.enqueue(new byte[]{'{', '"', 'x', '"', ':', '"', (byte) 0xff, '"', '}'}); utf8.run(); utf8.assertTransferCount(JsonRecordTransform.FAILURE, 1);
        var duplicateRule = runner(); duplicateRule.setProperty(JsonRecordTransform.OPERATIONS, "[{\"op\":\"constant\",\"output\":\"a\",\"output\":\"b\",\"value\":1}]");
        duplicateRule.enqueue("{}"); duplicateRule.run(); duplicateRule.assertTransferCount(JsonRecordTransform.FAILURE, 1);
    }
    @Test void limitsCoverInputRulesDepthAndOutputExpansion() {
        for (String input : List.of("{\"large\":\"" + "a".repeat(256 * 1024) + "\"}", "[" + "{},".repeat(1000) + "{}]", "{\"deep\":" + "[".repeat(40) + "0" + "]".repeat(40) + "}")) {
            var runner = runner(); runner.enqueue(input); runner.run(); runner.assertTransferCount(JsonRecordTransform.FAILURE, 1);
        }
        var rules = runner(); rules.setProperty(JsonRecordTransform.OPERATIONS, JSON.toJSONString(List.of(Map.of("op", "constant", "output", "v", "value", "a".repeat(32768)))));
        rules.enqueue("{}"); rules.run(); rules.assertTransferCount(JsonRecordTransform.FAILURE, 1);
        var expanded = runner(); expanded.setProperty(JsonRecordTransform.OPERATIONS, JSON.toJSONString(List.of(Map.of("op", "constant", "output", "large", "value", "汉".repeat(9000)))));
        expanded.enqueue("[" + "{},".repeat(99) + "{}]"); expanded.run(); expanded.assertTransferCount(JsonRecordTransform.FAILURE, 1); expanded.assertTransferCount(JsonRecordTransform.SUCCESS, 0);
    }
    @Test void draftRulesAreCreatableButCannotExecuteUnknownOperations() {
        var runner = TestRunners.newTestRunner(JsonRecordTransform.class); runner.assertValid(); runner.enqueue("{}"); runner.run(); runner.assertTransferCount(JsonRecordTransform.FAILURE, 1);
        var unknown = runner(); unknown.setProperty(JsonRecordTransform.OPERATIONS, "[{\"op\":\"eval\",\"script\":\"ignored\"}]"); unknown.enqueue("{}"); unknown.run(); unknown.assertTransferCount(JsonRecordTransform.FAILURE, 1);
    }
    @Test void contentRepositoryReadFailureRollsBackRatherThanClaimingBadInput() {
        var runner = configure(TestRunners.newTestRunner(new JsonRecordTransform() {
            @Override protected byte[] readInput(ProcessSession session, FlowFile input) throws IOException { throw new IOException("simulated repository failure"); }
        }));
        runner.enqueue(record("test")); assertThrows(AssertionError.class, runner::run);
        runner.assertTransferCount(JsonRecordTransform.FAILURE, 0); runner.assertTransferCount(JsonRecordTransform.SUCCESS, 0); assertEquals(1, runner.getQueueSize().getObjectCount());
    }
    @Test void ecmascriptNumbersAreOptInAndSerializeWithJavascriptThresholds() {
        String document = "{\"integer\":9007199254740993,\"one\":1,\"negativeZero\":-0,\"small\":1e-7,\"fixedSmall\":1e-6,\"fixedLarge\":1e20,\"large\":1e21,\"tiny\":5e-324,\"overflow\":1e400}";
        var legacy = runner(); legacy.setProperty(JsonRecordTransform.OPERATIONS, "[{\"op\":\"parse\",\"input\":\"/payload\",\"document\":\"doc\",\"numberMode\":\"ECMASCRIPT_DOUBLE\"},{\"op\":\"serialize\",\"document\":\"doc\",\"output\":\"payload\"}]");
        legacy.enqueue(JSON.toJSONString(Map.of("payload", document))); legacy.run(); legacy.assertTransferCount(JsonRecordTransform.SUCCESS, 1);
        String result = JSON.parseObject(new String(legacy.getFlowFilesForRelationship(JsonRecordTransform.SUCCESS).get(0).toByteArray(), StandardCharsets.UTF_8)).getString("payload");
        assertEquals("{\"integer\":9007199254740992,\"one\":1,\"negativeZero\":0,\"small\":1e-7,\"fixedSmall\":0.000001,\"fixedLarge\":100000000000000000000,\"large\":1e+21,\"tiny\":5e-324,\"overflow\":null}", result);
        var exact = runner(); exact.setProperty(JsonRecordTransform.OPERATIONS, "[{\"op\":\"parse\",\"input\":\"/payload\",\"document\":\"doc\"},{\"op\":\"serialize\",\"document\":\"doc\",\"output\":\"payload\"}]");
        exact.enqueue(JSON.toJSONString(Map.of("payload", "{\"integer\":9007199254740993}"))); exact.run();
        assertTrue(JSON.parseObject(new String(exact.getFlowFilesForRelationship(JsonRecordTransform.SUCCESS).get(0).toByteArray(), StandardCharsets.UTF_8)).getString("payload").contains("9007199254740993"));
    }
    @Test void ecmascriptDecimalConversionCoversShortestRoundTripAndArrayKeyOrder() {
        assertEquals("0.1", JsonRecordTransform.ecmaNumber(0.1)); assertEquals("1000000000000000100", JsonRecordTransform.ecmaNumber(1000000000000000100d));
        assertEquals("-5e-324", JsonRecordTransform.ecmaNumber(-Double.MIN_VALUE)); assertEquals("1.7976931348623157e+308", JsonRecordTransform.ecmaNumber(Double.MAX_VALUE));
        assertEquals("0", JsonRecordTransform.ecmaNumber(-0.0)); assertEquals("null", JsonRecordTransform.ecmaNumber(Double.NaN));
        var runner = runner(); runner.setProperty(JsonRecordTransform.OPERATIONS, "[{\"op\":\"parse\",\"input\":\"/payload\",\"document\":\"doc\",\"numberMode\":\"ECMASCRIPT_DOUBLE\"},{\"op\":\"serialize\",\"document\":\"doc\",\"output\":\"payload\"}]");
        runner.enqueue(JSON.toJSONString(Map.of("payload", "{\"b\":1,\"10\":2,\"2\":3,\"01\":4}"))); runner.run();
        String result = JSON.parseObject(new String(runner.getFlowFilesForRelationship(JsonRecordTransform.SUCCESS).get(0).toByteArray(), StandardCharsets.UTF_8)).getString("payload");
        assertEquals("{\"2\":3,\"10\":2,\"b\":1,\"01\":4}", result);
    }
    @Test void recordFiltersProduceCompleteOrderedArrayOrExplicitEmpty() {
        var runner = runner(); runner.setProperty(JsonRecordTransform.OPERATIONS, "[{\"op\":\"filter\",\"input\":\"/flag\",\"operator\":\"EQ\",\"value\":true}]");
        runner.enqueue("[{\"flag\":true,\"n\":1},{\"flag\":false,\"n\":2},{\"flag\":true,\"n\":3}]"); runner.run();
        var output = runner.getFlowFilesForRelationship(JsonRecordTransform.SUCCESS).get(0); output.assertAttributeEquals("governance.transform.input.records", "3"); output.assertAttributeEquals("record.count", "2");
        var rows = JSON.parseArray(new String(output.toByteArray(), StandardCharsets.UTF_8)); assertEquals(1, rows.getJSONObject(0).getIntValue("n")); assertEquals(3, rows.getJSONObject(1).getIntValue("n"));
        runner.clearTransferState(); runner.enqueue("{\"flag\":false}"); runner.run(); runner.assertTransferCount(JsonRecordTransform.EMPTY, 1);
        runner.getFlowFilesForRelationship(JsonRecordTransform.EMPTY).get(0).assertContentEquals("{\"flag\":false}");
    }
}
