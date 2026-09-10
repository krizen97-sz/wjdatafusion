package com.hm.governance.compatibility;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Bounded data operations only: no expressions, scripting, reflection, network or filesystem access. */
public final class JsonRecordTransform {
    public static final int MAX_ROWS = 1000;
    public static final int MAX_OPERATIONS = 64;
    public static final int MAX_DEPTH = 32;
    public static final int MAX_VALUES = 50000;
    public static final int MAX_CONTENT_UNITS = 2 * 1024 * 1024;
    public static final int MAX_DOCUMENT_BYTES = 256 * 1024;
    private static final Object MISSING = new Object();
    public interface Codec {
        Object parse(String text); String stringify(Object value);
        default Object parse(String text, boolean ecmascriptDouble) { return parse(text); }
        default Object parse(String text, String numberMode) {
            if (numberMode.equals("KETTLE_JSON")) throw invalid("Codec does not support Kettle JSON numbers");
            return parse(text, numberMode.equals("ECMASCRIPT_DOUBLE"));
        }
        default String kettleString(Object value) {
            if (value instanceof String || value instanceof Number || value instanceof Boolean) return value.toString();
            return stringify(value);
        }
        default String stringify(Object value, boolean ecmascriptDouble) {
            if (ecmascriptDouble) throw invalid("Codec does not support ECMAScript number serialization");
            return stringify(value);
        }
        /** Null represents an invalid native date; configuration/zone errors must throw. */
        default Long legacyDateParse(String text, String zone) {
            throw invalid("Codec does not support RHINO_DATE parsing");
        }
    }
    public record Result(Object value, int inputRecords, int outputRecords) {
        public boolean empty() { return outputRecords == 0; }
    }
    private interface Operation { void apply(State state); }
    private static class State {
        final Map<String, Object> row;
        final Map<String, Object> documents = new LinkedHashMap<>();
        final Set<String> ecmaDocuments = new java.util.HashSet<>();
        final Set<String> nonFiniteDocuments = new java.util.HashSet<>();
        boolean keep = true;
        State(Map<String, Object> row) { this.row = row; }
    }
    private final List<Operation> operations;
    private final Codec codec;

    public JsonRecordTransform(List<?> rules, Codec codec) {
        this.codec = Objects.requireNonNull(codec, "JSON codec required");
        if (rules == null || rules.isEmpty() || rules.size() > MAX_OPERATIONS) throw invalid("Operation count limit");
        this.operations = new ArrayList<>();
        for (Object rule : (List<?>) copy(rules)) operations.add(compile(object(rule)));
    }

    public Result apply(Object input) {
        boolean one = input instanceof Map<?, ?>;
        Object safe = copy(input);
        List<?> rows = one ? List.of(safe) : array(safe);
        if (rows.size() > MAX_ROWS) throw invalid("Input row limit");
        List<Map<String, Object>> output = new ArrayList<>();
        Budget total = new Budget();
        for (Object value : rows) {
            State state = new State(object(value));
            for (Operation operation : operations) {
                operation.apply(state);
                validate(state);
                if (!state.keep) break;
            }
            if (!state.keep) continue;
            // Do not mutate input or publish any row until the entire batch succeeds.
            measure(state.row, 0, total, identitySet());
            output.add(state.row);
        }
        return new Result(one && !output.isEmpty() ? output.get(0) : output, rows.size(), output.size());
    }

    private Operation compile(Map<String, Object> rule) {
        String op = string(rule, "op");
        return switch (op) {
            case "parse" -> {
                keys(rule, "op", "input", "document", "numberMode", "onEmpty");
                List<String> input = pointer(string(rule, "input")); String document = name(string(rule, "document"));
                String numberMode = choice(rule, "numberMode", "EXACT", "EXACT", "ECMASCRIPT_DOUBLE", "KETTLE_JSON");
                String onEmpty = choice(rule, "onEmpty", "FAIL", "FAIL", "NULL");
                yield state -> {
                    String text = text(required(resolve(state.row, input)));
                    if (text.length() > MAX_DOCUMENT_BYTES || text.getBytes(StandardCharsets.UTF_8).length > MAX_DOCUMENT_BYTES) throw invalid("Document byte limit");
                    boolean legacyNumbers = numberMode.equals("ECMASCRIPT_DOUBLE"), kettleNumbers = numberMode.equals("KETTLE_JSON");
                    boolean nullableDocument = kettleNumbers && onEmpty.equals("NULL");
                    if (nullableDocument) {
                        int first = 0;
                        while (first < text.length() && " \t\r\n\uFEFF".indexOf(text.charAt(first)) >= 0) first++;
                        text = text.substring(first);
                    }
                    if (text.isEmpty() && !onEmpty.equals("NULL")) throw invalid("Empty document");
                    Object parsed = text.isEmpty() ? null : copy(codec.parse(text, numberMode), legacyNumbers || kettleNumbers);
                    if (!(parsed instanceof Map<?, ?>) && !(parsed instanceof List<?>) && !(nullableDocument || text.isEmpty() && onEmpty.equals("NULL"))) throw invalid("Document must be object or array");
                    if (legacyNumbers || kettleNumbers) state.nonFiniteDocuments.add(document); else state.nonFiniteDocuments.remove(document);
                    if (legacyNumbers) {
                        state.ecmaDocuments.add(document); parsed = doubleNumbers(parsed);
                    } else state.ecmaDocuments.remove(document);
                    state.documents.put(document, parsed);
                };
            }
            case "get" -> {
                keys(rule, "op", "document", "path", "output", "type", "trim", "missing");
                String document = optionalName(rule, "document"); List<String> path = pointer(string(rule, "path"));
                String output = name(string(rule, "output")); String type = choice(rule, "type", "VALUE", "VALUE", "STRING", "KETTLE_STRING");
                String trim = choice(rule, "trim", "NONE", "NONE", "BOTH", "START", "END");
                String missing = choice(rule, "missing", "FAIL", "FAIL", "NULL");
                if (!Set.of("STRING", "KETTLE_STRING").contains(type) && !trim.equals("NONE")) throw invalid("Trimming requires string type");
                yield state -> {
                    Object value = resolve(document == null ? state.row : document(state, document), path, type.equals("KETTLE_STRING") && missing.equals("NULL"));
                    if (value == MISSING) { if (missing.equals("FAIL")) throw invalid("Required path missing"); value = null; }
                    if (value != null && type.equals("STRING")) value = trim(text(value), trim);
                    if (value != null && type.equals("KETTLE_STRING")) {
                        String converted = codec.kettleString(value);
                        if (converted == null || converted.length() > MAX_CONTENT_UNITS || converted.getBytes(StandardCharsets.UTF_8).length > MAX_CONTENT_UNITS) throw invalid("Stringified value limit");
                        value = trim(converted, trim);
                    }
                    state.row.put(output, copy(value));
                };
            }
            case "constant" -> {
                keys(rule, "op", "output", "value");
                String output = name(string(rule, "output"));
                if (!rule.containsKey("value")) throw invalid("Constant value required");
                Object value = copy(rule.get("value"));
                yield state -> state.row.put(output, copy(value));
            }
            case "remove" -> {
                keys(rule, "op", "output"); String output = name(string(rule, "output"));
                yield state -> state.row.remove(output);
            }
            case "filter" -> {
                keys(rule, "op", "input", "operator", "value", "keep");
                List<String> input = pointer(string(rule, "input"));
                String operator = choice(rule, "operator", "EQ", "EQ", "NE", "IS_NULL", "IS_NOT_NULL");
                boolean equality = operator.equals("EQ") || operator.equals("NE");
                if (equality != rule.containsKey("value")) throw invalid("Equality requires value; null predicates omit value");
                Object expected = rule.get("value");
                if (expected instanceof Map<?, ?> || expected instanceof List<?>) throw invalid("Filter value must be scalar");
                boolean keep = !rule.containsKey("keep") || bool(rule, "keep");
                yield state -> {
                    Object actual = resolve(state.row, input);
                    if (actual instanceof Map<?, ?> || actual instanceof List<?>) throw invalid("Filter input must be scalar");
                    boolean hit = switch (operator) {
                        case "IS_NULL" -> actual == null;
                        case "IS_NOT_NULL" -> actual != MISSING && actual != null;
                        case "EQ" -> actual != MISSING && equal(actual, expected);
                        default -> actual != MISSING && !equal(actual, expected);
                    };
                    state.keep = hit == keep;
                };
            }
            case "copy" -> {
                keys(rule, "op", "input", "output", "ifNotNull");
                List<String> input = pointer(string(rule, "input")); String output = name(string(rule, "output")); boolean conditional = bool(rule, "ifNotNull");
                yield state -> { Object value = required(resolve(state.row, input)); if (value != null || !conditional) state.row.put(output, copy(value)); };
            }
            case "trim" -> {
                keys(rule, "op", "input", "output", "mode");
                List<String> input = pointer(string(rule, "input")); String output = name(string(rule, "output"));
                String mode = choice(rule, "mode", "BOTH", "NONE", "BOTH", "START", "END");
                yield state -> state.row.put(output, nullableString(state, input, value -> trim(value, mode)));
            }
            case "replace" -> {
                keys(rule, "op", "input", "output", "find", "replacement", "mode");
                List<String> input = pointer(string(rule, "input")); String output = name(string(rule, "output"));
                String find = string(rule, "find"), replacement = string(rule, "replacement");
                if (find.isEmpty()) throw invalid("Replacement search cannot be empty");
                String mode = choice(rule, "mode", "FIRST", "FIRST", "ALL");
                yield state -> state.row.put(output, nullableString(state, input, value -> replace(value, find, replacement, mode.equals("ALL"))));
            }
            case "substring" -> {
                keys(rule, "op", "input", "output", "start", "end");
                List<String> input = pointer(string(rule, "input")); String output = name(string(rule, "output"));
                int start = integer(rule, "start"), end = integer(rule, "end");
                if (end < start) throw invalid("Substring end precedes start");
                yield state -> state.row.put(output, nullableString(state, input, value -> value.substring(Math.min(start, value.length()), Math.min(end, value.length()))));
            }
            case "set", "broadcast" -> {
                keys(rule, "op", "document", "path", "input", "ifNotNull", "array", "outerIndex");
                boolean broadcast = op.equals("broadcast");
                if (!broadcast && (rule.containsKey("array") || rule.containsKey("outerIndex"))) throw invalid("Array options require broadcast");
                String document = name(string(rule, "document")); List<String> path = pointer(string(rule, "path"));
                List<String> input = pointer(string(rule, "input")); boolean conditional = bool(rule, "ifNotNull");
                List<String> array = broadcast ? pointer(string(rule, "array")) : List.of(); boolean outerIndex = bool(rule, "outerIndex");
                if (outerIndex && !path.contains("$index")) throw invalid("Outer index token missing");
                yield state -> {
                    Object value = required(resolve(state.row, input));
                    if (value == null && conditional) return;
                    Object doc = document(state, document);
                    if (!broadcast) write(doc, path, copy(value));
                    else {
                        List<?> items = array(required(resolve(doc, array)));
                        if (items.size() > MAX_ROWS) throw invalid("Broadcast row limit");
                        for (int i = 0; i < items.size(); i++) {
                            String index = Integer.toString(i);
                            List<String> target = outerIndex ? path.stream().map(token -> token.equals("$index") ? index : token).toList() : path;
                            write(items.get(i), target, copy(value));
                            validate(state); // Bound expansion while broadcasting, not after constructing a huge document.
                        }
                    }
                };
            }
            case "serialize" -> {
                keys(rule, "op", "document", "output");
                String document = name(string(rule, "document")); String output = name(string(rule, "output"));
                yield state -> {
                    String text = codec.stringify(document(state, document), state.ecmaDocuments.contains(document));
                    if (text == null || text.length() > MAX_CONTENT_UNITS || text.getBytes(StandardCharsets.UTF_8).length > MAX_CONTENT_UNITS) throw invalid("Serialized document byte limit");
                    state.row.put(output, text);
                };
            }
            case "dateGate" -> {
                keys(rule, "op", "input", "output", "pattern", "threshold", "zone", "onInvalid", "parser");
                List<String> input = pointer(string(rule, "input")); String output = name(string(rule, "output"));
                String parser = choice(rule, "parser", "STRICT", "STRICT", "RHINO_DATE");
                String threshold = string(rule, "threshold"), zoneText = string(rule, "zone");
                if (threshold.length() > 128 || zoneText.length() > 128) throw invalid("Date configuration limit");
                ZoneId zone = ZoneId.of(zoneText);
                String onInvalid = choice(rule, "onInvalid", "FAIL", "FAIL", "FALSE");
                if (parser.equals("RHINO_DATE")) {
                    if (rule.containsKey("pattern")) throw invalid("pattern is supported only by STRICT dates");
                    Long boundary = codec.legacyDateParse(threshold, zoneText);
                    if (boundary == null) throw invalid("Valid RHINO_DATE threshold required");
                    yield state -> {
                        String value = text(required(resolve(state.row, input)));
                        if (value.length() > 256) throw invalid("Legacy date text limit");
                        Long instant = codec.legacyDateParse(value, zoneText);
                        if (instant == null && !onInvalid.equals("FALSE")) throw invalid("Invalid RHINO_DATE text");
                        state.row.put(output, instant != null && instant > boundary);
                    };
                }
                String pattern = string(rule, "pattern");
                if (pattern.length() > 128) throw invalid("Date configuration limit");
                DateTimeFormatter format = DateTimeFormatter.ofPattern(pattern, Locale.ROOT).withResolverStyle(ResolverStyle.STRICT);
                var boundary = instant(threshold, format, zone);
                yield state -> {
                    String text = text(required(resolve(state.row, input)));
                    if (text.length() > 1024) throw invalid("Date text limit");
                    try { state.row.put(output, instant(text, format, zone).isAfter(boundary)); }
                    catch (java.time.DateTimeException error) {
                        if (!onInvalid.equals("FALSE")) throw error;
                        state.row.put(output, false);
                    }
                };
            }
            default -> throw invalid("Unknown operation");
        };
    }

    private static java.time.Instant instant(String value, DateTimeFormatter format, ZoneId zone) {
        LocalDateTime date = LocalDateTime.parse(value, format);
        var offsets = zone.getRules().getValidOffsets(date);
        if (offsets.size() != 1) throw new java.time.DateTimeException("Ambiguous or nonexistent local time");
        return date.toInstant(offsets.get(0));
    }
    private static Object nullableString(State state, List<String> input, java.util.function.UnaryOperator<String> transform) {
        Object value = required(resolve(state.row, input));
        return value == null ? null : transform.apply(text(value));
    }
    private static String trim(String value, String mode) {
        int start = 0, end = value.length();
        if (mode.equals("BOTH") || mode.equals("START")) while (start < end && value.charAt(start) <= 0x20) start++;
        if (mode.equals("BOTH") || mode.equals("END")) while (end > start && value.charAt(end - 1) <= 0x20) end--;
        return value.substring(start, end);
    }
    private static String replace(String value, String find, String replacement, boolean all) {
        StringBuilder result = new StringBuilder(); int start = 0, index;
        while ((index = value.indexOf(find, start)) >= 0) {
            if ((long) result.length() + index - start + replacement.length() > MAX_CONTENT_UNITS) throw invalid("String expansion limit");
            result.append(value, start, index).append(replacement); start = index + find.length();
            if (!all) break;
        }
        if ((long) result.length() + value.length() - start > MAX_CONTENT_UNITS) throw invalid("String expansion limit");
        return result.append(value, start, value.length()).toString();
    }
    private static Object document(State state, String name) {
        if (!state.documents.containsKey(name)) throw invalid("Temporary document missing");
        return state.documents.get(name);
    }
    private static Object required(Object value) { if (value == MISSING) throw invalid("Required path missing"); return value; }
    private static void keys(Map<String, Object> rule, String... keys) { if (!Set.of(keys).containsAll(rule.keySet())) throw invalid("Unknown operation field"); }
    private static String string(Map<String, Object> rule, String key) { return text(required(rule.containsKey(key) ? rule.get(key) : MISSING)); }
    private static String text(Object value) { if (!(value instanceof String string)) throw invalid("String value required"); return string; }
    private static String name(String value) {
        if (value.isBlank() || value.length() > 128 || value.chars().anyMatch(Character::isISOControl)) throw invalid("Invalid field name");
        return value;
    }
    private static String optionalName(Map<String, Object> rule, String key) { return rule.containsKey(key) ? name(string(rule, key)) : null; }
    private static boolean bool(Map<String, Object> rule, String key) {
        if (!rule.containsKey(key)) return false;
        if (!(rule.get(key) instanceof Boolean value)) throw invalid("Boolean option required");
        return value;
    }
    private static String choice(Map<String, Object> rule, String key, String fallback, String... choices) {
        String value = rule.containsKey(key) ? string(rule, key) : fallback;
        if (!List.of(choices).contains(value)) throw invalid("Invalid operation choice");
        return value;
    }
    private static int integer(Map<String, Object> rule, String key) {
        Object value = rule.get(key);
        if (!(value instanceof Number number)) throw invalid("Integer option required");
        try {
            int result = new BigDecimal(number.toString()).intValueExact();
            if (result < 0 || result > MAX_CONTENT_UNITS) throw invalid("Integer option limit");
            return result;
        } catch (ArithmeticException | NumberFormatException error) { throw invalid("Integer option required"); }
    }
    private static List<String> pointer(String path) {
        if (!path.startsWith("/") || path.length() > 1024) throw invalid("JSON Pointer required");
        List<String> tokens = new ArrayList<>();
        for (String token : path.substring(1).split("/", -1)) {
            StringBuilder decoded = new StringBuilder();
            for (int i = 0; i < token.length(); i++) {
                char ch = token.charAt(i);
                if (ch != '~') decoded.append(ch);
                else {
                    if (++i >= token.length() || (token.charAt(i) != '0' && token.charAt(i) != '1')) throw invalid("Invalid pointer escape");
                    decoded.append(token.charAt(i) == '0' ? '~' : '/');
                }
            }
            tokens.add(decoded.toString());
        }
        if (tokens.size() > MAX_DEPTH) throw invalid("Pointer depth limit");
        return tokens;
    }
    private static Object resolve(Object current, List<String> tokens) { return resolve(current, tokens, false); }
    private static Object resolve(Object current, List<String> tokens, boolean missingArrayMember) {
        for (String token : tokens) {
            if (current instanceof Map<?, ?> map) current = map.containsKey(token) ? map.get(token) : MISSING;
            else if (current instanceof List<?> list) {
                int index;
                try { index = index(token); }
                catch (IllegalArgumentException invalidIndex) { if (missingArrayMember) return MISSING; throw invalidIndex; }
                current = index < list.size() ? list.get(index) : MISSING;
            }
            else return MISSING;
        }
        return current;
    }
    private static int index(String value) {
        if (!value.matches("0|[1-9][0-9]*") || value.length() > 9) throw invalid("Invalid array index");
        return Integer.parseInt(value);
    }
    @SuppressWarnings("unchecked") private static void write(Object root, List<String> path, Object value) {
        Object parent = required(resolve(root, path.subList(0, path.size() - 1))); String leaf = path.get(path.size() - 1);
        if (parent instanceof Map<?, ?> map) ((Map<String, Object>) map).put(leaf, value);
        else if (parent instanceof List<?> list) {
            int index = index(leaf); if (index >= list.size()) throw invalid("Write array index missing");
            ((List<Object>) list).set(index, value);
        } else throw invalid("Write parent is not a container");
    }
    @SuppressWarnings("unchecked") private static Map<String, Object> object(Object value) {
        if (!(value instanceof Map<?, ?>)) throw invalid("Object record required");
        return (Map<String, Object>) value;
    }
    private static List<?> array(Object value) { if (!(value instanceof List<?> list)) throw invalid("Array required"); return list; }
    private static void validate(State state) {
        Budget budget = new Budget(); measure(state.row, 0, budget, identitySet());
        for (var document : state.documents.entrySet()) {
            budget.allowNonFinite = state.nonFiniteDocuments.contains(document.getKey());
            budget.units += document.getKey().length(); measure(document.getValue(), 1, budget, identitySet());
        }
    }
    private static class Budget { int values; long units; boolean allowNonFinite; }
    private static boolean equal(Object actual, Object expected) {
        if (actual instanceof Number a && expected instanceof Number b) return new BigDecimal(a.toString()).compareTo(new BigDecimal(b.toString())) == 0;
        return Objects.equals(actual, expected);
    }
    private static Object doubleNumbers(Object value) {
        if (value instanceof Number number) return number.doubleValue();
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>(); map.forEach((key, child) -> result.put((String) key, doubleNumbers(child))); return result;
        }
        if (value instanceof List<?> list) { List<Object> result = new ArrayList<>(); for (Object child : list) result.add(doubleNumbers(child)); return result; }
        return value;
    }
    private static Set<Object> identitySet() { return Collections.newSetFromMap(new IdentityHashMap<>()); }
    private static Object copy(Object value) { return copy(value, false); }
    private static Object copy(Object value, boolean allowNonFinite) {
        Budget budget = new Budget(); budget.allowNonFinite = allowNonFinite;
        measure(value, 0, budget, identitySet());
        return cloneValue(value);
    }
    private static Object cloneValue(Object value) {
        if (value instanceof Map<?, ?> map) { Map<String, Object> result = new LinkedHashMap<>(); map.forEach((key, child) -> result.put((String) key, cloneValue(child))); return result; }
        if (value instanceof List<?> list) { List<Object> result = new ArrayList<>(); for (Object child : list) result.add(cloneValue(child)); return result; }
        return value;
    }
    private static void measure(Object value, int depth, Budget budget, Set<Object> ancestors) {
        if (depth > MAX_DEPTH || ++budget.values > MAX_VALUES) throw invalid("JSON structure limit");
        budget.units += value instanceof String text ? text.length() : value instanceof Number number ? number.toString().length() : 8;
        if (budget.units > MAX_CONTENT_UNITS) throw invalid("JSON content limit");
        if (value == null || value instanceof String || value instanceof Boolean || value instanceof BigDecimal || value instanceof BigInteger
            || value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) return;
        if (value instanceof Double d && (Double.isFinite(d) || budget.allowNonFinite) || value instanceof Float f && (Float.isFinite(f) || budget.allowNonFinite)) return;
        if (!ancestors.add(value)) throw invalid("Cyclic JSON structure");
        try {
            if (value instanceof Map<?, ?> map) {
                for (var entry : map.entrySet()) {
                    if (!(entry.getKey() instanceof String key)) throw invalid("String JSON keys required");
                    budget.units += key.length(); measure(entry.getValue(), depth + 1, budget, ancestors);
                }
            } else if (value instanceof List<?> list) { for (Object child : list) measure(child, depth + 1, budget, ancestors); }
            else throw invalid("Unsupported JSON value");
        } finally { ancestors.remove(value); }
    }
    private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }
}
