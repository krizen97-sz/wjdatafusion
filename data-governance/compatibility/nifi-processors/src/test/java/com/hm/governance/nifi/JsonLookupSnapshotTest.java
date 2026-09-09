package com.hm.governance.nifi;

import static org.junit.jupiter.api.Assertions.*;
import com.alibaba.fastjson2.JSON;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import org.junit.jupiter.api.Test;

class JsonLookupSnapshotTest {
    private static final String SNAPSHOT = "[{\"plate\":\"苏A\",\"color\":\"blue\",\"code\":\"matched\",\"enabled\":true}]";
    private TestRunner runner() { return configure(TestRunners.newTestRunner(JsonLookupSnapshot.class)); }
    private TestRunner configure(TestRunner runner) {
        runner.setProperty(JsonLookupSnapshot.LOOKUP_ROWS, SNAPSHOT);
        runner.setProperty(JsonLookupSnapshot.MATCH_FIELDS, "[{\"input\":\"/plate\",\"lookup\":\"plate\",\"type\":\"STRING\",\"operator\":\"EQ\"},{\"input\":\"/color\",\"lookup\":\"color\",\"type\":\"STRING\",\"operator\":\"EQ\"},{\"lookup\":\"enabled\",\"type\":\"STRING\",\"operator\":\"IS_NOT_NULL\"}]");
        runner.setProperty(JsonLookupSnapshot.RETURN_FIELDS, "[{\"lookup\":\"code\",\"output\":\"code\",\"default\":\"0\"}]");
        return runner;
    }
    @Test void completeArrayEnrichmentPreservesSourceFieldsAndReportsSnapshotHash() throws Exception {
        var runner = runner(); runner.enqueue("[{\"plate\":\"苏A\",\"color\":\"blue\",\"keep\":null},{\"plate\":\"苏A\",\"color\":\"red\"}]"); runner.run();
        runner.assertTransferCount(JsonLookupSnapshot.SUCCESS, 1); runner.assertTransferCount(JsonLookupSnapshot.FAILURE, 0);
        var output = runner.getFlowFilesForRelationship(JsonLookupSnapshot.SUCCESS).get(0);
        var records = JSON.parseArray(new String(output.toByteArray(), StandardCharsets.UTF_8));
        assertEquals("matched", records.getJSONObject(0).getString("code")); assertTrue(records.getJSONObject(0).containsKey("keep"));
        assertEquals("0", records.getJSONObject(1).get("code"));
        output.assertAttributeEquals("governance.lookup.input.records", "2"); output.assertAttributeEquals("governance.lookup.output.records", "2");
        output.assertAttributeEquals("governance.lookup.matched.records", "1"); output.assertAttributeEquals("governance.lookup.unmatched.records", "1");
        output.assertAttributeEquals("governance.lookup.snapshot.sha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(SNAPSHOT.getBytes(StandardCharsets.UTF_8))));
    }
    @Test void objectInputStaysObjectAndMissingDefaultIsExplicitNull() {
        var runner = runner(); runner.setProperty(JsonLookupSnapshot.RETURN_FIELDS, "[{\"lookup\":\"code\",\"output\":\"code\"}]");
        runner.enqueue("{\"plate\":\"苏B\",\"color\":\"blue\"}"); runner.run();
        var output = JSON.parseObject(new String(runner.getFlowFilesForRelationship(JsonLookupSnapshot.SUCCESS).get(0).toByteArray(), StandardCharsets.UTF_8));
        assertTrue(output.containsKey("code")); assertNull(output.get("code")); assertEquals("苏B", output.get("plate"));
    }
    @Test void numericJsonFractionsDoNotLosePrecisionInTheParser() {
        var runner = runner(); runner.setProperty(JsonLookupSnapshot.LOOKUP_ROWS, "[{\"id\":9007199254740993.10000000000000000001,\"code\":\"precise\"}]");
        runner.setProperty(JsonLookupSnapshot.MATCH_FIELDS, "[{\"input\":\"/id\",\"lookup\":\"id\",\"type\":\"NUMBER\",\"operator\":\"EQ\"}]");
        runner.enqueue("[{\"id\":9007199254740993.10000000000000000001},{\"id\":9007199254740993.10000000000000000002}]"); runner.run();
        var records = JSON.parseArray(new String(runner.getFlowFilesForRelationship(JsonLookupSnapshot.SUCCESS).get(0).toByteArray(), StandardCharsets.UTF_8));
        assertEquals("precise", records.getJSONObject(0).getString("code")); assertEquals("0", records.getJSONObject(1).getString("code"));
        assertTrue(new String(runner.getFlowFilesForRelationship(JsonLookupSnapshot.SUCCESS).get(0).toByteArray(), StandardCharsets.UTF_8).contains("9007199254740993.10000000000000000002"));
    }
    @Test void laterRecordFailureTransfersOriginalWholeBatchWithoutAnySuccessOrDataLeak() {
        var runner = runner(); runner.setProperty(JsonLookupSnapshot.MISSING_MATCH, "FAIL");
        String original = "[{\"plate\":\"苏A\",\"color\":\"blue\"},{\"plate\":\"private-miss\",\"color\":\"blue\"}]";
        runner.enqueue(original); runner.run(); runner.assertTransferCount(JsonLookupSnapshot.SUCCESS, 0); runner.assertTransferCount(JsonLookupSnapshot.FAILURE, 1);
        var failure = runner.getFlowFilesForRelationship(JsonLookupSnapshot.FAILURE).get(0); failure.assertContentEquals(original);
        assertFalse(failure.getAttribute("governance.error").contains("private-miss"));
    }
    @Test void emptyAndAllDroppedBatchesHaveExplicitCountsAndNoSuccess() {
        for (String input : new String[]{"[]", "{\"plate\":\"苏B\",\"color\":\"blue\"}"}) {
            var runner = runner(); runner.setProperty(JsonLookupSnapshot.MISSING_MATCH, "DROP"); runner.enqueue(input); runner.run();
            runner.assertTransferCount(JsonLookupSnapshot.EMPTY, 1); runner.assertTransferCount(JsonLookupSnapshot.SUCCESS, 0);
            var output = runner.getFlowFilesForRelationship(JsonLookupSnapshot.EMPTY).get(0); output.assertContentEquals(input);
            output.assertAttributeEquals("governance.lookup.output.records", "0");
        }
    }
    @Test void draftPropertiesAreCreatableButInvalidRulesCannotExecute() {
        var runner = TestRunners.newTestRunner(JsonLookupSnapshot.class); runner.assertValid(); runner.enqueue("{}"); runner.run();
        runner.assertTransferCount(JsonLookupSnapshot.FAILURE, 1); runner.assertTransferCount(JsonLookupSnapshot.SUCCESS, 0);
        var unknownRule = runner(); unknownRule.setProperty(JsonLookupSnapshot.RETURN_FIELDS, "[{\"lookup\":\"code\",\"output\":\"code\",\"typo\":true}]");
        unknownRule.enqueue("{}"); unknownRule.run(); unknownRule.assertTransferCount(JsonLookupSnapshot.FAILURE, 1);
    }
    @Test void malformedUtf8AndInvalidJsonNeverBecomePartialRecords() throws Exception {
        var bytes = new ByteArrayOutputStream(); bytes.write("{\"plate\":\"".getBytes(StandardCharsets.UTF_8)); bytes.write(0xff); bytes.write("\"}".getBytes(StandardCharsets.UTF_8));
        for (byte[] input : new byte[][]{bytes.toByteArray(), "".getBytes(StandardCharsets.UTF_8), "[{}] trailing".getBytes(StandardCharsets.UTF_8), "[1]".getBytes(StandardCharsets.UTF_8)}) {
            var runner = runner(); runner.enqueue(input); runner.run(); runner.assertTransferCount(JsonLookupSnapshot.FAILURE, 1); runner.assertTransferCount(JsonLookupSnapshot.SUCCESS, 0);
        }
    }
    @Test void bytesRowsAndDepthAreBoundedBeforeOutput() {
        for (String input : new String[]{"{\"large\":\"" + "a".repeat(256 * 1024) + "\"}", "[" + "{},".repeat(1000) + "{}]", "{\"deep\":" + "[".repeat(40) + "0" + "]".repeat(40) + "}"}) {
            var runner = runner(); runner.enqueue(input); runner.run(); runner.assertTransferCount(JsonLookupSnapshot.FAILURE, 1); runner.assertTransferCount(JsonLookupSnapshot.SUCCESS, 0);
        }
        var runner = runner(); runner.setProperty(JsonLookupSnapshot.LOOKUP_ROWS, "[" + "{},".repeat(1000) + "{}]"); runner.enqueue("{}"); runner.run(); runner.assertTransferCount(JsonLookupSnapshot.FAILURE, 1);
    }
    @Test void duplicateSnapshotMatchesAreRejectedByDefault() {
        var runner = runner(); runner.setProperty(JsonLookupSnapshot.LOOKUP_ROWS, SNAPSHOT.substring(0, SNAPSHOT.length() - 1) + "," + SNAPSHOT.substring(1));
        runner.enqueue("{\"plate\":\"苏A\",\"color\":\"blue\"}"); runner.run(); runner.assertTransferCount(JsonLookupSnapshot.FAILURE, 1); runner.assertTransferCount(JsonLookupSnapshot.SUCCESS, 0);
    }
    @Test void duplicateJsonKeysCannotSilentlyChangeAMatchOrRule() {
        var input = runner(); input.enqueue("{\"plate\":null,\"plate\":\"苏A\",\"color\":\"blue\"}"); input.run();
        input.assertTransferCount(JsonLookupSnapshot.FAILURE, 1); input.assertTransferCount(JsonLookupSnapshot.SUCCESS, 0);
        var snapshot = runner(); snapshot.setProperty(JsonLookupSnapshot.LOOKUP_ROWS, "[{\"plate\":\"苏B\",\"plate\":\"苏A\",\"color\":\"blue\",\"code\":\"matched\",\"enabled\":true}]");
        snapshot.enqueue("{\"plate\":\"苏A\",\"color\":\"blue\"}"); snapshot.run(); snapshot.assertTransferCount(JsonLookupSnapshot.FAILURE, 1);
        var rule = runner(); rule.setProperty(JsonLookupSnapshot.RETURN_FIELDS, "[{\"lookup\":\"code\",\"output\":\"code\",\"output\":\"other\"}]");
        rule.enqueue("{}"); rule.run(); rule.assertTransferCount(JsonLookupSnapshot.FAILURE, 1);
    }
    @Test void repositoryFailureRollsBackInsteadOfBecomingValidationFailure() {
        var runner = configure(TestRunners.newTestRunner(new JsonLookupSnapshot() {
            @Override protected byte[] readInput(ProcessSession session, FlowFile input) throws IOException { throw new IOException("simulated repository failure"); }
        }));
        runner.enqueue("{}"); assertThrows(AssertionError.class, runner::run);
        runner.assertTransferCount(JsonLookupSnapshot.FAILURE, 0); runner.assertTransferCount(JsonLookupSnapshot.SUCCESS, 0);
        assertEquals(1, runner.getQueueSize().getObjectCount());
    }
}
