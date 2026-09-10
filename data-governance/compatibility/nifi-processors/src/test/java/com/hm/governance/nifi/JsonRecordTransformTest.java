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
    @Test void ecmascriptDecimalConversionCoversShortestRoundTripAndLegacyInsertionOrder() {
        assertEquals("0.1", JsonRecordTransform.ecmaNumber(0.1)); assertEquals("1000000000000000100", JsonRecordTransform.ecmaNumber(1000000000000000100d));
        assertEquals("-5e-324", JsonRecordTransform.ecmaNumber(-Double.MIN_VALUE)); assertEquals("1.7976931348623157e+308", JsonRecordTransform.ecmaNumber(Double.MAX_VALUE));
        assertEquals("0", JsonRecordTransform.ecmaNumber(-0.0)); assertEquals("null", JsonRecordTransform.ecmaNumber(Double.NaN));
        var runner = runner(); runner.setProperty(JsonRecordTransform.OPERATIONS, "[{\"op\":\"parse\",\"input\":\"/payload\",\"document\":\"doc\",\"numberMode\":\"ECMASCRIPT_DOUBLE\"},{\"op\":\"serialize\",\"document\":\"doc\",\"output\":\"payload\"}]");
        runner.enqueue(JSON.toJSONString(Map.of("payload", "{\"b\":1,\"10\":2,\"2\":3,\"01\":4}"))); runner.run();
        String result = JSON.parseObject(new String(runner.getFlowFilesForRelationship(JsonRecordTransform.SUCCESS).get(0).toByteArray(), StandardCharsets.UTF_8)).getString("payload");
        assertEquals("{\"b\":1,\"10\":2,\"2\":3,\"01\":4}", result);
    }
    @Test void recordFiltersProduceCompleteOrderedArrayOrExplicitEmpty() {
        var runner = runner(); runner.setProperty(JsonRecordTransform.OPERATIONS, "[{\"op\":\"filter\",\"input\":\"/flag\",\"operator\":\"EQ\",\"value\":true}]");
        runner.enqueue("[{\"flag\":true,\"n\":1},{\"flag\":false,\"n\":2},{\"flag\":true,\"n\":3}]"); runner.run();
        var output = runner.getFlowFilesForRelationship(JsonRecordTransform.SUCCESS).get(0); output.assertAttributeEquals("governance.transform.input.records", "3"); output.assertAttributeEquals("record.count", "2");
        var rows = JSON.parseArray(new String(output.toByteArray(), StandardCharsets.UTF_8)); assertEquals(1, rows.getJSONObject(0).getIntValue("n")); assertEquals(3, rows.getJSONObject(1).getIntValue("n"));
        runner.clearTransferState(); runner.enqueue("{\"flag\":false}"); runner.run(); runner.assertTransferCount(JsonRecordTransform.EMPTY, 1);
        runner.getFlowFilesForRelationship(JsonRecordTransform.EMPTY).get(0).assertContentEquals("{\"flag\":false}");
    }
    @Test void legacyRhinoFixtureOrderAndExtremeNumbersMatchTheObservedDocumentContract() {
        // Synthetic values and key order from the supplied Rhino 1.7R3 oracle; no business endpoints.
        String document = "{\"keys\":{\"9\":1,\"2\":2,\"01\":3,\"1\":4,\"4294967295\":5,\"plain\":6},\"negativeZero\":-0.0,\"beyondFinite\":1e400,\"underflow\":1e-4000,\"negativeOverflow\":-1e400,\"negativeUnderflow\":-1e-4000}";
        assertEquals("{\"keys\":{\"9\":1,\"2\":2,\"01\":3,\"1\":4,\"4294967295\":5,\"plain\":6},\"negativeZero\":0,\"beyondFinite\":null,\"underflow\":0,\"negativeOverflow\":null,\"negativeUnderflow\":0}", legacyDocument(document));
        assertEquals("{\"a\":[null,0,0],\"text\":\"quoted 1e-4000 and 9007199254740993\"}", legacyDocument("{\"a\":[1e999999,1e-999999,-0],\"text\":\"quoted 1e-4000 and 9007199254740993\"}"));
        assertEquals("null", JsonRecordTransform.ecmaNumber(Double.POSITIVE_INFINITY));
        assertEquals("null", JsonRecordTransform.ecmaNumber(Double.NEGATIVE_INFINITY));
        assertEquals("null", JsonRecordTransform.ecmaNumber(Double.NaN));
    }
    @Test void legacyNumericBudgetAndStrictTokensRejectWithoutChangingTheOriginalBatch() {
        for (String number : List.of("1".repeat(1025), "1e1000000", "1e-0004000", "01", "+1", ".1", "1.", "1e", "1e+-2", "NaN", "Infinity", "-Infinity")) {
            String original = JSON.toJSONString(Map.of("payload", "{\"n\":" + number + "}"));
            var runner = legacyRunner(); runner.enqueue(original); runner.run();
            runner.assertTransferCount(JsonRecordTransform.FAILURE, 1); runner.assertTransferCount(JsonRecordTransform.SUCCESS, 0);
            var file = runner.getFlowFilesForRelationship(JsonRecordTransform.FAILURE).get(0); file.assertContentEquals(original);
            assertFalse(file.getAttribute("governance.error").contains(number));
        }
        assertEquals("{\"n\":null}", legacyDocument("{\"n\":" + "1".repeat(1024) + "}"));
    }
    @Test void exactModeStillKeepsLargeIntegersAndDecimalDigitsWithoutDoubleCoercion() {
        String original = "{\"9\":9007199254740993,\"2\":9007199254740993.123456789012345678901,\"underflow\":1e-400}";
        var runner = runner(); runner.setProperty(JsonRecordTransform.OPERATIONS,
            "[{\"op\":\"parse\",\"input\":\"/payload\",\"document\":\"doc\"},{\"op\":\"serialize\",\"document\":\"doc\",\"output\":\"payload\"}]");
        runner.enqueue(JSON.toJSONString(Map.of("payload", original))); runner.run(); runner.assertTransferCount(JsonRecordTransform.SUCCESS, 1);
        String output = JSON.parseObject(new String(runner.getFlowFilesForRelationship(JsonRecordTransform.SUCCESS).get(0).toByteArray(), StandardCharsets.UTF_8)).getString("payload");
        assertTrue(output.contains("9007199254740993.123456789012345678901"));
        assertEquals(new java.math.BigDecimal("1e-400"), JSON.parseObject(output).getBigDecimal("underflow"));
        assertTrue(output.indexOf("\"9\"") < output.indexOf("\"2\""));
    }
    private TestRunner legacyRunner() {
        var runner = runner(); runner.setProperty(JsonRecordTransform.OPERATIONS,
            "[{\"op\":\"parse\",\"input\":\"/payload\",\"document\":\"doc\",\"numberMode\":\"ECMASCRIPT_DOUBLE\"},{\"op\":\"serialize\",\"document\":\"doc\",\"output\":\"payload\"}]");
        return runner;
    }
    private String legacyDocument(String document) {
        var runner = legacyRunner(); runner.enqueue(JSON.toJSONString(Map.of("payload", document))); runner.run();
        runner.assertTransferCount(JsonRecordTransform.SUCCESS, 1); runner.assertTransferCount(JsonRecordTransform.FAILURE, 0);
        return JSON.parseObject(new String(runner.getFlowFilesForRelationship(JsonRecordTransform.SUCCESS).get(0).toByteArray(), StandardCharsets.UTF_8)).getString("payload");
    }

    @Test void kettleJsonNumbersFollowObservedTokenLengthAndRetainNegativeZero() {
        String[][] cases = {{"1.0", "1.0"}, {"1e20", "1.0E20"}, {"9007199254740993", "9007199254740993"},
            {"1e400", "Infinity"}, {"-1e400", "-Infinity"}, {"1e-4000", "0.0"}, {"-0.0", "-0.0"},
            {"0.123456789012345678901", "0.123456789012345678901"}, {"12345.00", "12345.0"},
            {"1e-19", "1.0E-19"}, {"0.0000000000000000001", "1E-19"}, {"true", "true"}, {"false", "false"}};
        for (String[] sample : cases) assertEquals(sample[1], kettleValue("{\"value\":" + sample[0] + "}"));
        assertEquals("  source text  ", kettleValue("{\"value\":\"  source text  \"}"));
    }
    @Test void kettleStringContainersUseInsertionOrderAndJsonSmartEscapingOnlyForContainers() {
        String text = "slash/ quote\" back\\ newline\n tab\t 中文\u2028\u2029 🚀";
        assertEquals(text, kettleValue(JSON.toJSONString(Map.of("value", text))));
        assertEquals("{\"9\":9,\"2\":2,\"01\":1,\"plain\":[\"slash\\/\",{\"z\":null,\"a\":true}],\"unicode\":\"\\u2028\\u2029\\u20AF\"}",
            kettleValue("{\"value\":{\"9\":9,\"2\":2,\"01\":1,\"plain\":[\"slash/\",{\"z\":null,\"a\":true}],\"unicode\":\"\u2028\u2029\u20AF\"}}"));
        assertEquals("[{\"z\":1,\"a\":2},\"slash\\/\",null]", kettleValue("{\"value\":[{\"z\":1,\"a\":2},\"slash/\",null]}"));
    }
    @Test void explicitKettleEmptyPolicyHandlesMissingNullAndPrimitiveDocumentsAndFiltersNullSource() {
        for (String document : List.of("", " ", "\r\n\t", "\uFEFF", "\uFEFF{}", "null", "true", "123", "\"text\"", "[]", "{}", "{\"value\":null}"))
            assertNull(kettleValue(document));
        var runner = kettleRunner(); runner.enqueue("{\"payload\":null}"); runner.run();
        runner.assertTransferCount(JsonRecordTransform.EMPTY, 1); runner.assertTransferCount(JsonRecordTransform.FAILURE, 0);
        runner.getFlowFilesForRelationship(JsonRecordTransform.EMPTY).get(0).assertContentEquals("{\"payload\":null}");
    }
    @Test void kettleCompatibilityKeepsBadJsonAndNumericResourceBoundsClosed() {
        for (String document : List.of("{", "{\"value\":1} trailing", "{/*comment*/\"value\":1}", "{\"value\":NaN}", "{\"value\":Infinity}",
            "{\"value\":01}", "{\"value\":1e1000000}", "{\"value\":" + "1".repeat(1025) + "}", "{\"value\":1,\"value\":2}")) {
            String original = JSON.toJSONString(Map.of("payload", document)); var runner = kettleRunner(); runner.enqueue(original); runner.run();
            runner.assertTransferCount(JsonRecordTransform.FAILURE, 1); runner.assertTransferCount(JsonRecordTransform.SUCCESS, 0);
            runner.getFlowFilesForRelationship(JsonRecordTransform.FAILURE).get(0).assertContentEquals(original);
        }
    }
    private TestRunner kettleRunner() {
        var runner = runner(); runner.setProperty(JsonRecordTransform.OPERATIONS,
            "[{\"op\":\"filter\",\"input\":\"/payload\",\"operator\":\"IS_NOT_NULL\"},{\"op\":\"parse\",\"input\":\"/payload\",\"document\":\"doc\",\"numberMode\":\"KETTLE_JSON\",\"onEmpty\":\"NULL\"},{\"op\":\"get\",\"document\":\"doc\",\"path\":\"/value\",\"output\":\"out\",\"type\":\"KETTLE_STRING\",\"missing\":\"NULL\",\"trim\":\"NONE\"}]");
        return runner;
    }
    private String kettleValue(String document) {
        var runner = kettleRunner(); runner.enqueue(JSON.toJSONString(Map.of("payload", document))); runner.run();
        runner.assertTransferCount(JsonRecordTransform.SUCCESS, 1); runner.assertTransferCount(JsonRecordTransform.FAILURE, 0);
        return JSON.parseObject(new String(runner.getFlowFilesForRelationship(JsonRecordTransform.SUCCESS).get(0).toByteArray(), StandardCharsets.UTF_8)).getString("out");
    }

    @Test void nativeDateProcessorMatchesTheTwelveOriginalScriptVectors() {
        Object[][] cases = {
            {"2019-12-24T10:22:24", false}, {"2019-12-24T10:22:25", false}, {"2019-12-24T10:22:26", true},
            {"invalid-date", false}, {"2020-02-30T12:00:00", true}, {"2020-01-01T00:00", true},
            {"2020-01-01T00:00:00Z", true}, {"2020-01-01T00:00:00+08:00", true},
            {"2019-02-29T00:00:00", false}, {"2020-01-01", true}, {"", false}, {null, null}
        };
        for (Object[] sample : cases) {
            var runner = dateRunner("FALSE", "2019/12/24 10:22:25", BoundedRhinoDateParser.engineZoneId());
            String original = "{\"passTime\":" + JSON.toJSONString(sample[0]) + ",\"keep\":\"retained\"}";
            runner.enqueue(original); runner.run();
            if (sample[1] == null) {
                runner.assertTransferCount(JsonRecordTransform.FAILURE, 1); runner.assertTransferCount(JsonRecordTransform.SUCCESS, 0);
                runner.getFlowFilesForRelationship(JsonRecordTransform.FAILURE).get(0).assertContentEquals(original);
            } else {
                runner.assertTransferCount(JsonRecordTransform.SUCCESS, 1); runner.assertTransferCount(JsonRecordTransform.FAILURE, 0);
                var output = JSON.parseObject(new String(runner.getFlowFilesForRelationship(JsonRecordTransform.SUCCESS).get(0).toByteArray(), StandardCharsets.UTF_8));
                assertEquals(sample[1], output.get("c")); assertEquals(sample[0], output.get("passTime")); assertEquals("retained", output.get("keep")); assertFalse(output.containsKey("scratchDate"));
            }
            runner.shutdown();
        }
    }
    @Test void nativeDateFailureIsAtomicAndDoesNotInferSuccessFromEarlierRows() {
        var runner = dateRunner("FAIL", "2019/12/24 10:22:25", BoundedRhinoDateParser.engineZoneId());
        String original = "[{\"passTime\":\"2020-01-01T12:00:00\"},{\"passTime\":\"invalid-date\"}]";
        runner.enqueue(original); runner.run(); runner.assertTransferCount(JsonRecordTransform.FAILURE, 1); runner.assertTransferCount(JsonRecordTransform.SUCCESS, 0);
        runner.getFlowFilesForRelationship(JsonRecordTransform.FAILURE).get(0).assertContentEquals(original);
    }
    @Test void nativeDateRejectsBadThresholdPatternAndZoneEvenWhenInvalidTextMapsToFalse() {
        var threshold = dateRunner("FALSE", "not-a-valid-threshold", BoundedRhinoDateParser.engineZoneId());
        threshold.enqueue("{\"passTime\":\"2020-01-01\"}"); threshold.run(); threshold.assertTransferCount(JsonRecordTransform.FAILURE, 1);
        String other = java.time.ZoneId.of(BoundedRhinoDateParser.engineZoneId()).normalized().equals(java.time.ZoneOffset.UTC) ? "Asia/Shanghai" : "UTC";
        var zone = dateRunner("FALSE", "2019/12/24 10:22:25", other);
        zone.enqueue("{\"passTime\":\"invalid-date\"}"); zone.run(); zone.assertTransferCount(JsonRecordTransform.FAILURE, 1);
        assertTrue(zone.getFlowFilesForRelationship(JsonRecordTransform.FAILURE).get(0).getAttribute("governance.error").startsWith("RHINO_DATE_ZONE_MISMATCH"));
        var rules = new java.util.ArrayList<>(dateOperations("FALSE", "2019/12/24 10:22:25", BoundedRhinoDateParser.engineZoneId()));
        var gate = new java.util.LinkedHashMap<>(rules.get(5)); gate.put("pattern", "uuuu/MM/dd HH:mm:ss"); rules.set(5, gate);
        var pattern = runner(); pattern.setProperty(JsonRecordTransform.OPERATIONS, JSON.toJSONString(rules));
        pattern.enqueue("{\"passTime\":\"2020-01-01\"}"); pattern.run(); pattern.assertTransferCount(JsonRecordTransform.FAILURE, 1);
    }
    @Test void strictDatesAndExactNumbersRemainDefaultAfterAddingNativeDates() {
        var runner = runner(); runner.setProperty(JsonRecordTransform.OPERATIONS,
            "[{\"op\":\"parse\",\"input\":\"/payload\",\"document\":\"doc\"},{\"op\":\"serialize\",\"document\":\"doc\",\"output\":\"payload\"}," +
            "{\"op\":\"dateGate\",\"input\":\"/timestamp\",\"output\":\"after\",\"pattern\":\"uuuu/MM/dd HH:mm:ss\",\"threshold\":\"2030/01/01 00:00:00\",\"zone\":\"Pacific/Honolulu\",\"onInvalid\":\"FALSE\"}]");
        String payload = "{\"precise\":9007199254740993.1234567890123456789}";
        runner.enqueue(JSON.toJSONString(Map.of("payload", payload, "timestamp", "2030/02/30 12:00:00"))); runner.run(); runner.assertTransferCount(JsonRecordTransform.SUCCESS, 1);
        var output = JSON.parseObject(new String(runner.getFlowFilesForRelationship(JsonRecordTransform.SUCCESS).get(0).toByteArray(), StandardCharsets.UTF_8));
        assertEquals(payload, output.getString("payload")); assertEquals(false, output.get("after"));
    }
    private TestRunner dateRunner(String onInvalid, String threshold, String zone) {
        var runner = runner(); runner.setProperty(JsonRecordTransform.OPERATIONS, JSON.toJSONString(dateOperations(onInvalid, threshold, zone))); return runner;
    }
    private List<Map<String, Object>> dateOperations(String onInvalid, String threshold, String zone) {
        return List.of(
            Map.of("op", "copy", "input", "/passTime", "output", "scratchDate"),
            Map.of("op", "replace", "input", "/scratchDate", "output", "scratchDate", "find", "T", "replacement", " ", "mode", "FIRST"),
            Map.of("op", "replace", "input", "/scratchDate", "output", "scratchDate", "find", "+", "replacement", " ", "mode", "FIRST"),
            Map.of("op", "substring", "input", "/scratchDate", "output", "scratchDate", "start", 0, "end", 19),
            Map.of("op", "replace", "input", "/scratchDate", "output", "scratchDate", "find", "-", "replacement", "/", "mode", "ALL"),
            Map.of("op", "dateGate", "input", "/scratchDate", "output", "c", "parser", "RHINO_DATE", "threshold", threshold, "zone", zone, "onInvalid", onInvalid),
            Map.of("op", "remove", "output", "scratchDate"));
    }

}
