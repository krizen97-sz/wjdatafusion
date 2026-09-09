package com.hm.governance.compatibility;

import static org.junit.jupiter.api.Assertions.*;
import com.hm.governance.compatibility.LookupSnapshot.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LookupSnapshotTest {
    private Match eq(String pointer, String column) { return new Match(pointer, column, ValueType.STRING, Operator.EQ); }
    private LookupSnapshot lookup(List<?> rows, List<Match> matches, MissingMatch missing, MultipleMatches multiple) {
        return new LookupSnapshot(rows, matches, List.of(new ReturnField("code", "code", "0")), missing, multiple);
    }
    @Test void predicatesMustMatchTheSameRowAndUnmatchedDefaultStaysString() {
        var engine = lookup(List.of(Map.of("plate", "A", "color", "red", "code", "first"), Map.of("plate", "B", "color", "blue", "code", "second")),
            List.of(eq("/plate", "plate"), eq("/color", "color")), MissingMatch.KEEP, MultipleMatches.FAIL);
        var result = engine.apply(Map.of("plate", "A", "color", "blue", "original", 7));
        assertEquals(Map.of("plate", "A", "color", "blue", "original", 7, "code", "0"), result.value());
        assertEquals(1, result.unmatchedRecords()); assertEquals(0, result.matchedRecords());
    }
    @Test void isNotNullOnlyReadsLookupAndDoesNotCoerceItsValue() {
        var inactive = new LinkedHashMap<String, Object>(Map.of("plate", "A", "code", "no")); inactive.put("active", null);
        var engine = lookup(List.of(inactive, Map.of("plate", "A", "code", "yes", "active", false)),
            List.of(eq("/plate", "plate"), new Match(null, "active", ValueType.STRING, Operator.IS_NOT_NULL)), MissingMatch.KEEP, MultipleMatches.FAIL);
        assertEquals("yes", ((Map<?, ?>) engine.apply(Map.of("plate", "A")).value()).get("code"));
        assertThrows(IllegalArgumentException.class, () -> new Match("/active", "active", ValueType.STRING, Operator.IS_NOT_NULL));
    }
    @Test void missingAndExplicitNullNeverMatchAndMatchedNullIsNotDefaulted() {
        var row = new LinkedHashMap<String, Object>(); row.put("plate", null); row.put("code", "bad");
        var engine = lookup(List.of(row, Map.of("code", "missing")), List.of(eq("/plate", "plate")), MissingMatch.KEEP, MultipleMatches.FAIL);
        var input = new LinkedHashMap<String, Object>(); input.put("plate", null);
        assertEquals("0", ((Map<?, ?>) engine.apply(input).value()).get("code"));
        assertEquals("0", ((Map<?, ?>) engine.apply(Map.of()).value()).get("code"));
        row.put("plate", "A"); row.put("code", null);
        engine = lookup(List.of(row), List.of(eq("/plate", "plate")), MissingMatch.KEEP, MultipleMatches.FAIL);
        var result = (Map<?, ?>) engine.apply(Map.of("plate", "A")).value();
        assertTrue(result.containsKey("code")); assertNull(result.get("code"));
    }
    @Test void duplicateMatchesRequireAnExplicitOrderedPolicy() {
        var rows = List.of(Map.of("plate", "A", "code", "first"), Map.of("plate", "A", "code", "last"));
        assertThrows(IllegalArgumentException.class, () -> lookup(rows, List.of(eq("/plate", "plate")), MissingMatch.KEEP, MultipleMatches.FAIL).apply(Map.of("plate", "A")));
        assertEquals("first", ((Map<?, ?>) lookup(rows, List.of(eq("/plate", "plate")), MissingMatch.KEEP, MultipleMatches.FIRST).apply(Map.of("plate", "A")).value()).get("code"));
        assertEquals("last", ((Map<?, ?>) lookup(rows, List.of(eq("/plate", "plate")), MissingMatch.KEEP, MultipleMatches.LAST).apply(Map.of("plate", "A")).value()).get("code"));
    }
    @Test void numberComparisonPreservesPrecisionAndIgnoresDecimalScale() {
        var engine = lookup(List.of(Map.of("id", new BigDecimal("9007199254740993.10000000000000000001"), "code", "precise")),
            List.of(new Match("/id", "id", ValueType.NUMBER, Operator.EQ)), MissingMatch.KEEP, MultipleMatches.FAIL);
        assertEquals("precise", ((Map<?, ?>) engine.apply(Map.of("id", "9007199254740993.100000000000000000010")).value()).get("code"));
        assertEquals("0", ((Map<?, ?>) engine.apply(Map.of("id", "9007199254740993.10000000000000000002")).value()).get("code"));
        for (String invalid : List.of("NaN", " 1", "01", "1e100000", "+1")) assertThrows(IllegalArgumentException.class, () -> engine.apply(Map.of("id", invalid)));
    }
    @Test void stringComparisonNeverTrimsChangesCaseOrCoercesNumbers() {
        var engine = lookup(List.of(Map.of("id", "A", "code", "matched")), List.of(eq("/id", "id")), MissingMatch.KEEP, MultipleMatches.FAIL);
        assertEquals(0, engine.apply(Map.of("id", "a")).matchedRecords());
        assertEquals(0, engine.apply(Map.of("id", " A")).matchedRecords());
        assertThrows(IllegalArgumentException.class, () -> engine.apply(Map.of("id", 1)));
    }
    @Test void pointersDecodeEscapesAndTraverseExactArrayIndexes() {
        var engine = lookup(List.of(Map.of("id", "A", "code", "matched")), List.of(eq("/a~1b/~0value/0", "id")), MissingMatch.KEEP, MultipleMatches.FAIL);
        assertEquals(1, engine.apply(Map.of("a/b", Map.of("~value", List.of("A")))).matchedRecords());
        for (String invalid : List.of("id", "/~2", "/a~")) assertThrows(IllegalArgumentException.class, () -> eq(invalid, "id"));
        for (String index : List.of("01", "-", "-1", "+1", "9999999999999999999999999")) {
            var indexed = lookup(List.of(Map.of("id", "A", "code", "matched")), List.of(eq("/items/" + index, "id")), MissingMatch.KEEP, MultipleMatches.FAIL);
            assertThrows(IllegalArgumentException.class, () -> indexed.apply(Map.of("items", List.of("A"))));
        }
    }
    @Test void dropCountsUnmatchedRecordsAndEmptyObjectDropsHaveNoOutput() {
        var engine = lookup(List.of(Map.of("id", "A", "code", "ok")), List.of(eq("/id", "id")), MissingMatch.DROP, MultipleMatches.FAIL);
        var result = engine.apply(List.of(Map.of("id", "A"), Map.of("id", "B")));
        assertInstanceOf(List.class, result.value()); assertEquals(2, result.inputRecords()); assertEquals(1, result.outputRecords());
        assertEquals(1, result.matchedRecords()); assertEquals(1, result.unmatchedRecords());
        assertTrue(engine.apply(Map.of("id", "B")).empty()); assertTrue(engine.apply(List.of()).empty());
    }
    @Test void errorInLaterRecordDoesNotChangeAnyOriginalRecord() {
        var original = new LinkedHashMap<String, Object>(Map.of("id", "A", "nested", new ArrayList<>(List.of("original"))));
        var engine = lookup(List.of(Map.of("id", "A", "code", "ok")), List.of(eq("/id", "id")), MissingMatch.FAIL, MultipleMatches.FAIL);
        assertThrows(IllegalArgumentException.class, () -> engine.apply(List.of(original, Map.of("id", "B"))));
        assertFalse(original.containsKey("code")); assertEquals(List.of("original"), original.get("nested"));
    }
    @SuppressWarnings("unchecked") @Test void snapshotInputsAndDefaultsAreDefensivelyCopied() {
        var nested = new ArrayList<>(List.of("original"));
        var source = new LinkedHashMap<String, Object>(Map.of("id", "A", "nested", nested));
        var row = new LinkedHashMap<String, Object>(Map.of("id", "A", "code", new ArrayList<>(List.of("snapshot"))));
        var defaults = new ArrayList<>(List.of("default"));
        var field = new ReturnField("code", "code", defaults);
        var engine = new LookupSnapshot(List.of(row), List.of(eq("/id", "id")), List.of(field), MissingMatch.KEEP, MultipleMatches.FAIL);
        ((List<String>) row.get("code")).set(0, "changed"); defaults.set(0, "changed");
        var output = (Map<String, Object>) engine.apply(source).value();
        ((List<String>) output.get("nested")).set(0, "changed"); ((List<String>) output.get("code")).set(0, "changed");
        assertEquals(List.of("original"), source.get("nested")); assertFalse(source.containsKey("code"));
        assertEquals(List.of("snapshot"), ((Map<?, ?>) engine.apply(source).value()).get("code"));
        assertEquals(List.of("default"), ((Map<?, ?>) engine.apply(Map.of("id", "B")).value()).get("code"));
    }
    @Test void missingReturnColumnAndInvalidRulesAreRejected() {
        var engine = lookup(List.of(Map.of("id", "A")), List.of(eq("/id", "id")), MissingMatch.KEEP, MultipleMatches.FAIL);
        assertThrows(IllegalArgumentException.class, () -> engine.apply(Map.of("id", "A")));
        for (String invalid : List.of("", "/code", "items[0]", "line\nbreak")) assertThrows(IllegalArgumentException.class, () -> new ReturnField("code", invalid, null));
        assertThrows(IllegalArgumentException.class, () -> lookup(List.of(), List.of(), MissingMatch.KEEP, MultipleMatches.FAIL));
        assertThrows(IllegalArgumentException.class, () -> new LookupSnapshot(List.of(), List.of(eq("/id", "id")), List.of(new ReturnField("a", "same", null), new ReturnField("b", "same", null)), MissingMatch.KEEP, MultipleMatches.FAIL));
    }
    @Test void depthRowAndOutputAmplificationBudgetsAreEnforced() {
        var engine = lookup(List.of(), List.of(eq("/id", "id")), MissingMatch.KEEP, MultipleMatches.FAIL);
        Object nested = "leaf"; for (int i = 0; i < 34; i++) nested = List.of(nested);
        Object tooDeep = nested;
        assertThrows(IllegalArgumentException.class, () -> engine.apply(Map.of("nested", tooDeep)));
        assertThrows(IllegalArgumentException.class, () -> engine.apply(java.util.Collections.nCopies(1001, Map.of("id", "A"))));
        var amplified = new LookupSnapshot(List.of(), List.of(eq("/id", "id")), List.of(new ReturnField("x", "x", "x".repeat(32000))), MissingMatch.KEEP, MultipleMatches.FAIL);
        assertThrows(IllegalArgumentException.class, () -> amplified.apply(java.util.Collections.nCopies(100, Map.of("id", "A"))));
        var cyclic = new LinkedHashMap<String, Object>(); cyclic.put("cycle", cyclic);
        assertThrows(IllegalArgumentException.class, () -> engine.apply(cyclic));
    }
}
