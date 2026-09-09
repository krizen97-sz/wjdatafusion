package com.hm.governance.compatibility;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** A bounded, deterministic join against an explicitly supplied immutable snapshot. */
public final class LookupSnapshot {
    public static final int MAX_ROWS = 1000;
    public static final int MAX_FIELDS = 64;
    public static final int MAX_DEPTH = 32;
    public static final int MAX_VALUES = 50000;
    public static final int MAX_CONTENT_UNITS = 2 * 1024 * 1024;
    private static final Object MISSING = new Object();
    public enum ValueType { STRING, NUMBER }
    public enum Operator { EQ, IS_NOT_NULL }
    public enum MissingMatch { KEEP, DROP, FAIL }
    public enum MultipleMatches { FAIL, FIRST, LAST }

    public record Match(String input, String lookup, ValueType type, Operator operator) {
        public Match {
            column(lookup);
            Objects.requireNonNull(type, "Match type required");
            Objects.requireNonNull(operator, "Match operator required");
            if (operator == Operator.EQ) pointer(input);
            else if (input != null && !input.isEmpty()) throw invalid("IS_NOT_NULL does not read input");
        }
    }
    public record ReturnField(String lookup, String output, Object defaultValue) {
        public ReturnField {
            column(lookup);
            if (output == null || !output.matches("[\\p{L}_][\\p{L}\\p{N}_. -]{0,127}")) throw invalid("Invalid output field");
            defaultValue = copy(defaultValue);
        }
    }
    public record Result(Object value, int inputRecords, int outputRecords, int matchedRecords, int unmatchedRecords) {
        public boolean empty() { return outputRecords == 0; }
    }

    private final List<Map<String, Object>> rows;
    private final List<Match> matches;
    private final List<ReturnField> returns;
    private final List<List<String>> pointers;
    private final List<Object[]> comparableRows;
    private final MissingMatch missing;
    private final MultipleMatches multiple;

    public LookupSnapshot(List<?> rows, List<Match> matches, List<ReturnField> returns,
                          MissingMatch missing, MultipleMatches multiple) {
        if (rows == null || rows.size() > MAX_ROWS) throw invalid("Snapshot row limit");
        if (matches == null || matches.isEmpty() || matches.size() > MAX_FIELDS || matches.stream().anyMatch(Objects::isNull)) throw invalid("Match field limit");
        if (returns == null || returns.isEmpty() || returns.size() > MAX_FIELDS || returns.stream().anyMatch(Objects::isNull)) throw invalid("Return field limit");
        if (returns.stream().map(ReturnField::output).distinct().count() != returns.size()) throw invalid("Duplicate output field");
        this.matches = List.copyOf(matches);
        this.pointers = matches.stream().map(match -> match.operator() == Operator.EQ ? pointer(match.input()) : List.<String>of()).toList();
        this.returns = returns.stream().map(r -> new ReturnField(r.lookup(), r.output(), r.defaultValue())).toList();
        this.missing = Objects.requireNonNull(missing, "Missing policy required");
        this.multiple = Objects.requireNonNull(multiple, "Multiple policy required");
        this.rows = objectRows(copy(rows));
        this.comparableRows = new ArrayList<>();
        // Validate all comparable snapshot cells up front, even when an earlier predicate would miss.
        for (Map<String, Object> row : this.rows) {
            Object[] cells = new Object[this.matches.size()];
            for (int i = 0; i < this.matches.size(); i++) {
                Match match = this.matches.get(i);
                Object value = row.getOrDefault(match.lookup(), MISSING);
                cells[i] = match.operator() == Operator.EQ ? comparable(value, match.type()) : value;
            }
            this.comparableRows.add(cells);
        }
    }

    public Result apply(Object input) {
        boolean objectInput = input instanceof Map<?, ?>;
        Object safeInput = copy(input);
        List<Map<String, Object>> records = objectInput ? List.of(object(safeInput)) : objectRows(safeInput);
        if (records.size() > MAX_ROWS) throw invalid("Input row limit");
        List<Map<String, Object>> output = new ArrayList<>();
        int[] outputBudget = {0, 0};
        int matched = 0;
        int unmatched = 0;
        for (Map<String, Object> record : records) {
            List<Object> values = new ArrayList<>();
            for (int i = 0; i < matches.size(); i++) {
                Match match = matches.get(i);
                values.add(match.operator() == Operator.EQ ? comparable(resolve(record, pointers.get(i)), match.type()) : MISSING);
            }
            Map<String, Object> selected = null;
            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                Map<String, Object> row = rows.get(rowIndex);
                boolean hit = true;
                for (int i = 0; i < matches.size(); i++) {
                    Match match = matches.get(i);
                    Object cell = comparableRows.get(rowIndex)[i];
                    if (match.operator() == Operator.IS_NOT_NULL) hit &= cell != MISSING && cell != null;
                    else hit &= equal(values.get(i), cell);
                    if (!hit) break;
                }
                if (!hit) continue;
                if (selected != null && multiple == MultipleMatches.FAIL) throw invalid("Multiple lookup matches");
                if (selected == null || multiple == MultipleMatches.LAST) selected = row;
            }
            if (selected == null) {
                unmatched++;
                if (missing == MissingMatch.FAIL) throw invalid("Lookup match missing");
                if (missing == MissingMatch.DROP) continue;
            } else matched++;
            Map<String, Object> enriched = object(copy(record, 0, outputBudget, Collections.newSetFromMap(new IdentityHashMap<>())));
            for (ReturnField field : returns) {
                if (selected != null && !selected.containsKey(field.lookup())) throw invalid("Return column missing");
                enriched.put(field.output(), copy(selected == null ? field.defaultValue() : selected.get(field.lookup()), 0, outputBudget,
                    Collections.newSetFromMap(new IdentityHashMap<>())));
            }
            output.add(enriched);
        }
        // One whole result is returned only after every record has succeeded.
        Object value = objectInput && !output.isEmpty() ? output.get(0) : output;
        return new Result(value, records.size(), output.size(), matched, unmatched);
    }

    private static boolean equal(Object left, Object right) {
        if (left == MISSING || right == MISSING || left == null || right == null) return false;
        if (left instanceof BigDecimal a && right instanceof BigDecimal b) return a.compareTo(b) == 0;
        return left.equals(right);
    }
    private static Object comparable(Object value, ValueType type) {
        if (value == MISSING || value == null) return value;
        if (type == ValueType.STRING) {
            if (!(value instanceof String)) throw invalid("String comparison requires strings");
            return value;
        }
        if (!(value instanceof Number) && !(value instanceof String)) throw invalid("Number comparison requires decimal values");
        String decimal = value.toString();
        if (decimal.length() > 1024 || !decimal.matches("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?")) throw invalid("Invalid decimal value");
        try {
            BigDecimal number = new BigDecimal(decimal);
            if (Math.abs((long) number.scale()) > 10000) throw invalid("Decimal scale limit");
            return number;
        } catch (NumberFormatException error) { throw invalid("Invalid decimal value"); }
    }
    private static void column(String name) {
        if (name == null || name.isBlank() || name.length() > 128 || name.chars().anyMatch(Character::isISOControl)) throw invalid("Invalid lookup column");
    }
    private static List<String> pointer(String path) {
        if (path == null || !path.startsWith("/") || path.length() > 1024) throw invalid("Input must be JSON Pointer");
        List<String> tokens = new ArrayList<>();
        for (String token : path.substring(1).split("/", -1)) {
            StringBuilder decoded = new StringBuilder();
            for (int i = 0; i < token.length(); i++) {
                char ch = token.charAt(i);
                if (ch != '~') decoded.append(ch);
                else {
                    if (++i >= token.length() || (token.charAt(i) != '0' && token.charAt(i) != '1')) throw invalid("Invalid JSON Pointer escape");
                    decoded.append(token.charAt(i) == '0' ? '~' : '/');
                }
            }
            tokens.add(decoded.toString());
        }
        if (tokens.size() > MAX_DEPTH) throw invalid("Pointer depth limit");
        return tokens;
    }
    private static Object resolve(Object current, List<String> tokens) {
        for (String token : tokens) {
            if (current instanceof Map<?, ?> map) current = map.containsKey(token) ? map.get(token) : MISSING;
            else if (current instanceof List<?> list) {
                if (!token.matches("0|[1-9][0-9]*")) throw invalid("Invalid JSON Pointer array index");
                try {
                    long index = Long.parseLong(token);
                    current = index < list.size() ? list.get((int) index) : MISSING;
                } catch (NumberFormatException error) { throw invalid("JSON Pointer array index limit"); }
            } else return MISSING;
        }
        return current;
    }
    @SuppressWarnings("unchecked") private static Map<String, Object> object(Object value) {
        if (!(value instanceof Map<?, ?>)) throw invalid("Object record required");
        return (Map<String, Object>) value;
    }
    private static List<Map<String, Object>> objectRows(Object value) {
        if (!(value instanceof List<?> list)) throw invalid("Object array required");
        if (list.size() > MAX_ROWS) throw invalid("Row limit");
        return list.stream().map(LookupSnapshot::object).toList();
    }
    private static Object copy(Object value) { return copy(value, 0, new int[]{0, 0}, Collections.newSetFromMap(new IdentityHashMap<>())); }
    private static Object copy(Object value, int depth, int[] count, Set<Object> ancestors) {
        if (depth > MAX_DEPTH || ++count[0] > MAX_VALUES) throw invalid("JSON structure limit");
        count[1] += value instanceof String text ? text.length() : 8;
        if (count[1] > MAX_CONTENT_UNITS) throw invalid("JSON content limit");
        if (value == null || value instanceof String || value instanceof Boolean || value instanceof BigDecimal || value instanceof BigInteger
            || value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) return value;
        if (value instanceof Double d && Double.isFinite(d)) return d;
        if (value instanceof Float f && Float.isFinite(f)) return f;
        if (!ancestors.add(value)) throw invalid("Cyclic JSON structure");
        try {
            if (value instanceof Map<?, ?> map) {
                Map<String, Object> result = new LinkedHashMap<>();
                for (var entry : map.entrySet()) {
                    if (!(entry.getKey() instanceof String key)) throw invalid("JSON string keys required");
                    count[1] += key.length();
                    result.put(key, copy(entry.getValue(), depth + 1, count, ancestors));
                }
                return result;
            }
            if (value instanceof List<?> list) {
                List<Object> result = new ArrayList<>();
                for (Object item : list) result.add(copy(item, depth + 1, count, ancestors));
                return result;
            }
            throw invalid("Unsupported JSON value");
        } finally { ancestors.remove(value); }
    }
    private static IllegalArgumentException invalid(String reason) { return new IllegalArgumentException(reason); }
}
