package com.hm.governance.compatibility;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import com.hm.governance.compatibility.DelimitedBatchEncoder.*;

class DelimitedBatchEncoderTest {
    private final DelimitedBatchEncoder encoder = new DelimitedBatchEncoder();
    private Options options(int limit, CountBasis basis, long age) {
        return new Options(List.of("message", "picture"), "7C 1F", true, limit, basis, age, "\n");
    }
    private List<Row> rows(int count) {
        return IntStream.range(0, count).mapToObj(i -> new Row(List.of("数据" + i, ""), 0)).toList();
    }
    private List<Integer> counts(List<Segment> segments) { return segments.stream().map(Segment::records).toList(); }
    @Test void legacySeventyFiveIncludesHeader() {
        assertEquals(List.of(74), counts(encoder.encode(rows(74), options(75, CountBasis.KETTLE_HEADER_INCLUSIVE, 0), 1_000_000)));
        assertEquals(List.of(74, 1), counts(encoder.encode(rows(75), options(75, CountBasis.KETTLE_HEADER_INCLUSIVE, 0), 1_000_000)));
        assertEquals(List.of(74, 2), counts(encoder.encode(rows(76), options(75, CountBasis.KETTLE_HEADER_INCLUSIVE, 0), 1_000_000)));
    }
    @Test void legacyTwoHundredIncludesHeader() {
        assertEquals(List.of(199, 1), counts(encoder.encode(rows(200), options(200, CountBasis.KETTLE_HEADER_INCLUSIVE, 0), 1_000_000)));
        assertEquals(List.of(199, 199, 1), counts(encoder.encode(rows(399), options(200, CountBasis.KETTLE_HEADER_INCLUSIVE, 0), 1_000_000)));
    }
    @Test void generalModeCountsDataRecords() {
        assertEquals(List.of(75, 1), counts(encoder.encode(rows(76), options(75, CountBasis.DATA_RECORDS, 0), 1_000_000)));
    }
    @Test void writesExactDelimiterNullsAndUtf8WithoutCsvQuoting() {
        byte[] actual = encoder.encode(List.of(new Row(Arrays.asList("{\"中文\":1}", null), 0)), options(75, CountBasis.DATA_RECORDS, 0), 1_000_000).get(0).content();
        assertArrayEquals("message|\u001fpicture\n{\"中文\":1}|\u001f\n".getBytes(StandardCharsets.UTF_8), actual);
    }
    @Test void noInputProducesNoEmptyHeaderFile() { assertEquals(List.of(), encoder.encode(List.of(), options(75, CountBasis.DATA_RECORDS, 0), 100)); }
    @Test void timeRollsOnlyOnNextRecordAndStrictlyAfterThreshold() {
        List<Row> input = List.of(new Row(List.of("a", ""), 0), new Row(List.of("b", ""), 5000), new Row(List.of("c", ""), 5001));
        assertEquals(List.of(2, 1), counts(encoder.encode(input, options(0, CountBasis.KETTLE_HEADER_INCLUSIVE, 5000), 10000)));
    }
    @Test void legacyGlobalCounterSurvivesTimeRoll() {
        List<Row> input = List.of(new Row(List.of("a", ""), 0), new Row(List.of("b", ""), 1), new Row(List.of("c", ""), 6000), new Row(List.of("d", ""), 6001));
        assertEquals(List.of(2, 1, 1), counts(encoder.encode(input, options(5, CountBasis.KETTLE_HEADER_INCLUSIVE, 5000), 10000)));
    }
    @Test void rejectsAmbiguousOrUnboundedInputs() {
        assertThrows(IllegalArgumentException.class, () -> DelimitedBatchEncoder.parseDelimiter("$[1F]"));
        assertThrows(IllegalArgumentException.class, () -> DelimitedBatchEncoder.parseDelimiter("7G"));
        assertThrows(IllegalArgumentException.class, () -> encoder.encode(rows(1), options(75, CountBasis.DATA_RECORDS, 0), 2));
        assertThrows(IllegalArgumentException.class, () -> encoder.encode(List.of(new Row(List.of("only one"), 0)), options(75, CountBasis.DATA_RECORDS, 0), 100));
        assertThrows(IllegalArgumentException.class, () -> encoder.encode(List.of(new Row(List.of("a", ""), 1), new Row(List.of("b", ""), 0)), options(75, CountBasis.DATA_RECORDS, 0), 100));
    }
    @Test void noHeaderLegacyAndGeneralHaveSameCount() {
        Options o = new Options(List.of("message", "picture"), "7c1f", false, 75, CountBasis.KETTLE_HEADER_INCLUSIVE, 0, "\r\n");
        List<Segment> result = encoder.encode(rows(76), o, 100000);
        assertEquals(List.of(75, 1), counts(result));
        assertTrue(new String(result.get(0).content(), StandardCharsets.UTF_8).endsWith("\r\n"));
    }
    @Test void outputBytesAreDefensiveCopies() {
        Segment s = encoder.encode(rows(1), options(75, CountBasis.DATA_RECORDS, 0), 10000).get(0);
        byte expected=s.content()[0]; byte[] bytes=s.content(); bytes[0]=0; assertEquals(expected,s.content()[0]);
    }
}
