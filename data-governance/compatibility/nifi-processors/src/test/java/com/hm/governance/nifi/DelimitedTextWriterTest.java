package com.hm.governance.nifi;

import static org.junit.jupiter.api.Assertions.*;
import com.alibaba.fastjson2.JSON;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.IntStream;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.processor.ProcessSession;
import org.junit.jupiter.api.Test;

class DelimitedTextWriterTest {
    private TestRunner runner() {
        TestRunner r=TestRunners.newTestRunner(DelimitedTextWriter.class);
        r.setProperty(DelimitedTextWriter.FIELDS,"message,picture");
        return r;
    }
    @Test void legacyActualProcessorSplitsHeaderInclusiveBatch() {
        TestRunner r=runner();r.setProperty(DelimitedTextWriter.BASIS,"KETTLE_HEADER_INCLUSIVE");
        r.enqueue(JSON.toJSONString(IntStream.range(0,75).mapToObj(i->Map.of("message","中文"+i,"picture","")).toList()));r.run();
        r.assertTransferCount(DelimitedTextWriter.SUCCESS,2);r.assertTransferCount(DelimitedTextWriter.FAILURE,0);
        var files=r.getFlowFilesForRelationship(DelimitedTextWriter.SUCCESS);
        files.get(0).assertAttributeEquals("record.count","74");files.get(1).assertAttributeEquals("record.count","1");
        assertTrue(new String(files.get(0).toByteArray(),StandardCharsets.UTF_8).startsWith("message|\u001fpicture\n中文0|\u001f\n"));
    }
    @Test void generalModeAndEmptyRouteAreExplicit() {
        TestRunner r=runner();r.enqueue("[]");r.run();r.assertTransferCount(DelimitedTextWriter.EMPTY,1);r.assertTransferCount(DelimitedTextWriter.SUCCESS,0);
    }
    @Test void malformedPayloadIsRejectedWithoutLeakingContent() {
        TestRunner r=runner();r.enqueue("{privatePayload:bad}");r.run();r.assertTransferCount(DelimitedTextWriter.FAILURE,1);
        assertFalse(r.getFlowFilesForRelationship(DelimitedTextWriter.FAILURE).get(0).getAttribute("governance.error").contains("privatePayload"));
    }
    @Test void streamingClockCannotBeImplicitlyInvented() {
        TestRunner r=runner();r.setProperty(DelimitedTextWriter.AGE,"5000");r.enqueue("[{\"message\":\"a\",\"picture\":\"\"}]");r.run();r.assertTransferCount(DelimitedTextWriter.FAILURE,1);
    }
    @Test void arrivalMustBeExactNonnegativeLong() {
        for (String value : new String[]{"5000.9", "9223372036854775808", "-1"}) {
            TestRunner r=runner();r.setProperty(DelimitedTextWriter.ARRIVAL,"time");
            r.enqueue("[{\"message\":\"a\",\"picture\":\"\",\"time\":"+value+"}]");r.run();
            r.assertTransferCount(DelimitedTextWriter.FAILURE,1);r.assertTransferCount(DelimitedTextWriter.SUCCESS,0);
        }
    }
    @Test void malformedUtf8IsNeverSilentlyReplaced() throws Exception {
        TestRunner r=runner();ByteArrayOutputStream data=new ByteArrayOutputStream();
        data.write("[{\"message\":\"".getBytes(StandardCharsets.UTF_8));data.write(0xff);data.write("\",\"picture\":\"\"}]".getBytes(StandardCharsets.UTF_8));
        r.enqueue(data.toByteArray());r.run();r.assertTransferCount(DelimitedTextWriter.FAILURE,1);r.assertTransferCount(DelimitedTextWriter.SUCCESS,0);
    }
    @Test void repositoryReadFailurePropagatesForRollback() {
        TestRunner r=TestRunners.newTestRunner(new DelimitedTextWriter() {
            @Override protected byte[] readInput(ProcessSession session, FlowFile input) throws IOException { throw new IOException("simulated repository failure"); }
        });
        r.setProperty(DelimitedTextWriter.FIELDS,"message,picture");r.enqueue("[]");
        assertThrows(AssertionError.class,r::run);
        r.assertTransferCount(DelimitedTextWriter.FAILURE,0);r.assertTransferCount(DelimitedTextWriter.SUCCESS,0);
        assertEquals(1,r.getQueueSize().getObjectCount());
    }
}
