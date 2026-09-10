package com.hm.governance.compatibility;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonRecordTransformTest {
    private static Map<String, Object> map(Object... pairs) {
        Map<String, Object> value = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) value.put((String) pairs[i], pairs[i + 1]);
        return value;
    }
    private static class FixtureCodec implements JsonRecordTransform.Codec {
        final Object document; Object serialized;
        FixtureCodec(Object document) { this.document = document; }
        public Object parse(String text) { if (!text.equals("fixture-document")) throw new IllegalArgumentException(); return document; }
        public String stringify(Object value) { serialized = value; return "serialized-document"; }
    }
    private JsonRecordTransform transform(Map<String, Object>... rules) { return new JsonRecordTransform(List.of(rules), new FixtureCodec(map())); }
    @SuppressWarnings("unchecked") private static Map<String, Object> object(Object value) { return (Map<String, Object>) value; }

    @Test void extractionPreservesShapeOriginalFieldsNullsAndDecimalPrecision() {
        BigDecimal precise = new BigDecimal("9007199254740993.1234567890123456789");
        var transform = transform(map("op", "get", "path", "/nested/value", "output", "copied"), map("op", "constant", "output", "flag", "value", true));
        var input = map("nested", map("value", precise), "kept", null);
        var result = transform.apply(input);
        assertEquals(precise, object(result.value()).get("copied")); assertTrue(object(result.value()).containsKey("kept"));
        assertEquals(1, result.inputRecords()); assertEquals(1, result.outputRecords()); assertFalse(input.containsKey("copied"));
        assertInstanceOf(List.class, transform.apply(List.of(input)).value()); assertTrue(transform.apply(List.of()).empty());
    }
    @Test void parsedDocumentsStayTemporaryAndSerializeOnlyWhenRequested() {
        var original = map("events", List.of(map("name", "  ALPHA \t", "attrs", map())));
        var codec = new FixtureCodec(original);
        var transform = new JsonRecordTransform(List.of(
            map("op", "parse", "input", "/payload", "document", "doc"),
            map("op", "get", "document", "doc", "path", "/events/0/name", "output", "name", "type", "STRING", "trim", "BOTH"),
            map("op", "set", "document", "doc", "path", "/events/0/attrs/newField", "input", "/name"),
            map("op", "serialize", "document", "doc", "output", "payload")
        ), codec);
        var result = object(transform.apply(map("payload", "fixture-document", "unrelated", 17)).value());
        assertEquals("ALPHA", result.get("name")); assertEquals("serialized-document", result.get("payload")); assertEquals(17, result.get("unrelated")); assertFalse(result.containsKey("doc"));
        var event = object(((List<?>) object(codec.serialized).get("events")).get(0));
        assertEquals("ALPHA", object(event.get("attrs")).get("newField"));
        assertTrue(object(object(((List<?>) original.get("events")).get(0)).get("attrs")).isEmpty());
    }
    @Test void stringOperationsAreOrderedLiteralAndNullPreserving() {
        var transform = transform(
            map("op", "trim", "input", "/text", "output", "text", "mode", "BOTH"),
            map("op", "replace", "input", "/text", "output", "text", "find", ".", "replacement", "$", "mode", "FIRST"),
            map("op", "replace", "input", "/text", "output", "text", "find", ".", "replacement", "/", "mode", "ALL"),
            map("op", "substring", "input", "/text", "output", "text", "start", 0, "end", 100));
        assertEquals("a$b/c", object(transform.apply(map("text", "  a.b.c  ")).value()).get("text"));
        assertNull(object(transform.apply(map("text", null)).value()).get("text"));
        assertThrows(IllegalArgumentException.class, () -> transform.apply(map("text", 1)));
    }
    @Test void missingAndStrictStringTypesAreExplicit() {
        assertThrows(IllegalArgumentException.class, () -> transform(map("op", "get", "path", "/value", "output", "out", "type", "STRING")).apply(map("value", 5)));
        var missing = transform(map("op", "get", "path", "/absent/leaf", "output", "out", "missing", "NULL"));
        assertTrue(object(missing.apply(map()).value()).containsKey("out")); assertNull(object(missing.apply(map()).value()).get("out"));
        assertThrows(IllegalArgumentException.class, () -> transform(map("op", "get", "path", "/absent", "output", "out")).apply(map()));
    }
    @Test void broadcastCreatesLeavesAndSupportsExplicitOuterArrayIndex() {
        var codec = new FixtureCodec(map("events", List.of(map("targets", List.of(map())), map("targets", List.of(map(), map())))));
        var transform = new JsonRecordTransform(List.of(map("op", "parse", "input", "/payload", "document", "doc"),
            map("op", "broadcast", "document", "doc", "array", "/events", "path", "/targets/$index/newValue", "input", "/value", "outerIndex", true),
            map("op", "serialize", "document", "doc", "output", "payload")), codec);
        transform.apply(map("payload", "fixture-document", "value", "mapped"));
        var events = (List<?>) object(codec.serialized).get("events");
        assertEquals("mapped", object(((List<?>) object(events.get(0)).get("targets")).get(0)).get("newValue"));
        assertEquals("mapped", object(((List<?>) object(events.get(1)).get("targets")).get(1)).get("newValue"));
        assertTrue(object(((List<?>) object(events.get(1)).get("targets")).get(0)).isEmpty());
    }
    @Test void conditionalNullSkipsWritesButDoesNotHideMissingSourceFields() {
        var codec = new FixtureCodec(map("events", List.of(map("attrs", map("existing", "keep")))));
        var transform = new JsonRecordTransform(List.of(map("op", "parse", "input", "/payload", "document", "doc"),
            map("op", "broadcast", "document", "doc", "array", "/events", "path", "/attrs/existing", "input", "/value", "ifNotNull", true),
            map("op", "serialize", "document", "doc", "output", "payload")), codec);
        transform.apply(map("payload", "fixture-document", "value", null));
        assertEquals("keep", object(object(((List<?>) object(codec.serialized).get("events")).get(0)).get("attrs")).get("existing"));
        assertThrows(IllegalArgumentException.class, () -> transform.apply(map("payload", "fixture-document")));
    }
    @Test void outOfRangeBroadcastFailsAndNeverMutatesCallerDocuments() {
        var doc = map("events", List.of(map("targets", List.of(map())), map("targets", List.of(map()))));
        var transform = new JsonRecordTransform(List.of(map("op", "parse", "input", "/payload", "document", "doc"),
            map("op", "broadcast", "document", "doc", "array", "/events", "path", "/targets/$index/newValue", "input", "/value", "outerIndex", true)), new FixtureCodec(doc));
        assertThrows(IllegalArgumentException.class, () -> transform.apply(map("payload", "fixture-document", "value", "mapped")));
        assertTrue(object(((List<?>) object(((List<?>) doc.get("events")).get(0)).get("targets")).get(0)).isEmpty());
    }
    @Test void lateBatchFailureDoesNotMutateEarlierRecords() {
        var first = map("value", " good "); var bad = map("value", 7);
        assertThrows(IllegalArgumentException.class, () -> transform(map("op", "trim", "input", "/value", "output", "result")).apply(List.of(first, bad)));
        assertFalse(first.containsKey("result")); assertEquals(" good ", first.get("value"));
    }
    @Test void dateGateUsesExplicitStrictPatternZoneAndGreaterThan() {
        var transform = transform(map("op", "dateGate", "input", "/timestamp", "output", "after", "pattern", "uuuu/MM/dd HH:mm:ss", "threshold", "2030/01/01 12:00:00", "zone", "UTC"));
        assertEquals(false, object(transform.apply(map("timestamp", "2030/01/01 12:00:00")).value()).get("after"));
        assertEquals(true, object(transform.apply(map("timestamp", "2030/01/01 12:00:01")).value()).get("after"));
        assertThrows(Exception.class, () -> transform.apply(map("timestamp", "2030/02/31 12:00:00")));
        var dst = transform(map("op", "dateGate", "input", "/timestamp", "output", "after", "pattern", "uuuu/MM/dd HH:mm:ss", "threshold", "2030/01/01 12:00:00", "zone", "America/New_York"));
        assertThrows(Exception.class, () -> dst.apply(map("timestamp", "2030/03/10 02:30:00")));
    }
    @Test void pointerEscapingAndArrayIndicesAreUnambiguous() {
        var transform = transform(map("op", "get", "path", "/a~1b/~0name/0", "output", "out"));
        assertEquals("yes", object(transform.apply(map("a/b", map("~name", List.of("yes")))).value()).get("out"));
        for (String path : List.of("x", "/x~2", "/x~")) assertThrows(IllegalArgumentException.class, () -> transform(map("op", "get", "path", path, "output", "out")));
        assertThrows(IllegalArgumentException.class, () -> transform(map("op", "get", "path", "/items/01", "output", "out")).apply(map("items", List.of(1, 2))));
    }
    @Test void unknownRulesAndUnsafeExpansionAreRejected() {
        for (var rule : List.of(map("op", "eval", "script", "ignored"), map("op", "constant", "output", "x", "value", 1, "unknown", true),
            map("op", "replace", "input", "/x", "output", "x", "find", "", "replacement", "value"),
            map("op", "copy", "input", "/x", "output", "x", "ifNotNull", "true"))) assertThrows(IllegalArgumentException.class, () -> transform(rule));
        assertThrows(IllegalArgumentException.class, () -> new JsonRecordTransform(java.util.Collections.nCopies(65, map("op", "constant", "output", "x", "value", 1)), new FixtureCodec(map())));
        var expanding = transform(map("op", "replace", "input", "/text", "output", "text", "find", "a", "replacement", "b".repeat(10000), "mode", "ALL"));
        assertThrows(IllegalArgumentException.class, () -> expanding.apply(map("text", "a".repeat(1000))));
    }
    @Test void rowDepthValueAndCycleLimitsFailBeforePublishing() {
        var transform = transform(map("op", "constant", "output", "x", "value", null));
        assertThrows(IllegalArgumentException.class, () -> transform.apply(java.util.Collections.nCopies(1001, map())));
        List<Object> cycle = new ArrayList<>(); cycle.add(cycle); assertThrows(IllegalArgumentException.class, () -> transform.apply(map("cycle", cycle)));
        Object deep = 0; for (int i = 0; i < 40; i++) deep = List.of(deep); Object nested = deep;
        assertThrows(IllegalArgumentException.class, () -> transform.apply(map("deep", nested)));
        assertThrows(IllegalArgumentException.class, () -> transform.apply(map("nan", Double.NaN)));
        assertThrows(IllegalArgumentException.class, () -> transform.apply(map("values", java.util.Collections.nCopies(50001, 0))));
    }
    @Test void filterPreservesOrderUsesExactTypesAndSkipsLaterOperationsForDroppedRows() {
        var transform = transform(map("op", "filter", "input", "/status", "operator", "EQ", "value", "drop", "keep", false),
            map("op", "get", "path", "/required", "output", "copied"));
        var result = transform.apply(List.of(map("status", "drop"), map("status", "keep", "required", 1), map("status", "keep", "required", 2)));
        assertEquals(3, result.inputRecords()); assertEquals(2, result.outputRecords());
        assertEquals(1, object(((List<?>) result.value()).get(0)).get("copied")); assertEquals(2, object(((List<?>) result.value()).get(1)).get("copied"));
        assertTrue(transform.apply(map("status", "drop")).empty());
        var booleanFilter = transform(map("op", "filter", "input", "/flag", "operator", "EQ", "value", true));
        assertEquals(1, booleanFilter.apply(List.of(map("flag", true), map("flag", "true"), map("flag", 1), map("flag", null), map())).outputRecords());
        var numberFilter = transform(map("op", "filter", "input", "/n", "operator", "EQ", "value", new BigDecimal("1.00")));
        assertEquals(2, numberFilter.apply(List.of(map("n", 1), map("n", new BigDecimal("1.0")), map("n", "1"))).outputRecords());
    }
    @Test void filterMissingIsDifferentFromNullAndUnknownComparisonsAreRejected() {
        var rows = List.of(map(), map("x", null), map("x", "yes"));
        assertEquals(1, transform(map("op", "filter", "input", "/x", "operator", "IS_NULL")).apply(rows).outputRecords());
        assertEquals(1, transform(map("op", "filter", "input", "/x", "operator", "IS_NOT_NULL")).apply(rows).outputRecords());
        assertEquals(1, transform(map("op", "filter", "input", "/x", "operator", "NE", "value", null)).apply(rows).outputRecords());
        assertThrows(IllegalArgumentException.class, () -> transform(map("op", "filter", "input", "/x", "operator", "EQ")));
        assertThrows(IllegalArgumentException.class, () -> transform(map("op", "filter", "input", "/x", "operator", "IS_NULL", "value", null)));
        assertThrows(IllegalArgumentException.class, () -> transform(map("op", "filter", "input", "/x", "operator", "EQ", "value", List.of())));
    }
    @Test void invalidDateFalseIsOptInAndDoesNotHideInvalidConfiguration() {
        var rule = map("op", "dateGate", "input", "/timestamp", "output", "after", "pattern", "uuuu/MM/dd HH:mm:ss", "threshold", "2030/01/01 00:00:00", "zone", "UTC", "onInvalid", "FALSE");
        assertEquals(false, object(transform(rule).apply(map("timestamp", "invalid-date")).value()).get("after"));
        assertThrows(IllegalArgumentException.class, () -> transform(rule).apply(map("timestamp", null)));
        rule.put("threshold", "invalid-threshold"); assertThrows(Exception.class, () -> transform(rule));
    }
    @Test void explicitlyRemovedScratchFieldsDoNotChangeTheOriginalSource() {
        var transform = transform(map("op", "copy", "input", "/original", "output", "scratch"), map("op", "trim", "input", "/scratch", "output", "derived"), map("op", "remove", "output", "scratch"));
        var row = object(transform.apply(map("original", " value ", "keep", 9)).value());
        assertEquals(" value ", row.get("original")); assertEquals("value", row.get("derived")); assertEquals(9, row.get("keep")); assertFalse(row.containsKey("scratch"));
    }
    @Test void nonfiniteCodecNumbersAreAllowedOnlyInsideExplicitLegacyDocuments() {
        var codec = new FixtureCodec(map("overflow", Double.POSITIVE_INFINITY, "notANumber", Double.NaN)) {
            boolean requestedLegacy;
            @Override public Object parse(String text, boolean legacy) { requestedLegacy = legacy; return super.parse(text); }
            @Override public String stringify(Object value, boolean legacy) {
                assertTrue(requestedLegacy); assertTrue(legacy); serialized = value; return "serialized-legacy";
            }
        };
        var parse = map("op", "parse", "input", "/payload", "document", "doc", "numberMode", "ECMASCRIPT_DOUBLE");
        var serialize = map("op", "serialize", "document", "doc", "output", "payload");
        var legacy = new JsonRecordTransform(List.of(parse, serialize), codec);
        assertEquals("serialized-legacy", object(legacy.apply(map("payload", "fixture-document")).value()).get("payload"));
        assertEquals(Double.POSITIVE_INFINITY, object(codec.serialized).get("overflow"));
        assertTrue(Double.isNaN((Double) object(codec.serialized).get("notANumber")));
        assertThrows(IllegalArgumentException.class, () -> legacy.apply(map("payload", "fixture-document", "outsideDocument", Double.NaN)));
        var exact = new JsonRecordTransform(List.of(map("op", "parse", "input", "/payload", "document", "doc"), serialize), codec);
        assertThrows(IllegalArgumentException.class, () -> exact.apply(map("payload", "fixture-document")));
    }

}
