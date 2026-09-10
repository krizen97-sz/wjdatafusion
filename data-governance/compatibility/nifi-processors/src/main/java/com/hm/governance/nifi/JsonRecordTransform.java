package com.hm.governance.nifi;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONWriter;
import com.hm.governance.compatibility.JsonRecordTransform.Codec;
import com.hm.governance.compatibility.JsonRecordTransform.Result;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.nifi.annotation.behavior.InputRequirement;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.processor.AbstractProcessor;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processor.util.StandardValidators;

@Tags({"governance", "json", "fields", "transform", "records"})
@CapabilityDescription("Transforms a JSON object or object array using 1 to 64 explicit data operations, including row filtering. No user scripts, expressions, external I/O or regex. All retained records succeed together or the original batch is rejected. parse defaults to EXACT numbers; KETTLE_JSON explicitly reproduces JsonSmart token-based integer/decimal conversion, and get KETTLE_STRING preserves strings or encodes containers using its escaped JSON contract; opt-in ECMASCRIPT_DOUBLE reproduces the observed Rhino 1.7R3 document contract: IEEE-754 numeric precision, insertion-order object keys, and nonfinite numbers serialized as null. This is not modern JavaScript integer-key enumeration. dateGate defaults to STRICT Java dates. Explicit RHINO_DATE calls only the fixed native Date.parse function from the official version compared with supplied-tool Date vectors, never evaluates user code, and requires the captured engine JVM timezone with drift rejection. NaN is handled by FAIL or FALSE.")
@InputRequirement(InputRequirement.Requirement.INPUT_REQUIRED)
public class JsonRecordTransform extends AbstractProcessor {
    public static final PropertyDescriptor OPERATIONS = new PropertyDescriptor.Builder().name("Operations")
        .description("JSON array, 1 to 64 operations and maximum 32 KiB. Ops: parse, get, constant, copy, remove, trim, replace, substring, set, broadcast, serialize, dateGate, filter. remove deletes only its explicit root output field. Input and path are strict JSON Pointers. STRING is a strict type check, not numeric coercion. KETTLE_STRING preserves null, stringifies number/boolean, and encodes containers with insertion order and JsonSmart escaping. Use trim NONE to retain original text. Trimming removes characters <= U+0020. replace is literal with FIRST or ALL; null string values remain null. get missing defaults to FAIL, optionally NULL. set/broadcast may create an object leaf but never missing intermediate containers or array slots. broadcast outerIndex=true substitutes a complete $index path token. parse numberMode defaults EXACT; ECMASCRIPT_DOUBLE explicitly loses precision and preserves Rhino 1.7R3 insertion-order object keys. KETTLE_JSON preserves integer precision and uses Double for decimal tokens up to 18 characters, BigDecimal for longer tokens. Both compatibility modes limit numeric tokens to 1024 characters and exponent fields to 6 digits. parse onEmpty defaults FAIL; NULL accepts empty text, and with KETTLE_JSON also JSON whitespace/BOM prefixes and valid scalar/null documents. Malformed JSON still fails; null source fields must be filtered before parsing. dateGate parser defaults STRICT and requires pattern, threshold and zone. RHINO_DATE forbids pattern, requires a valid threshold and the engine JVM zone, and accepts at most 256 date characters; it calls only native Date.parse with String data, without script evaluation. A changed engine JVM timezone fails explicitly. onInvalid defaults FAIL, optionally FALSE for invalid text only; comparison is strict greater-than. Preprocessing such as replacing T or truncating timezone suffixes remains explicit separate operations. filter supports scalar EQ/NE/IS_NULL/IS_NOT_NULL and keep=true(default)/false(invert). Missing is distinct from null and matches no predicate; numeric comparison is exact decimal, with no string/boolean coercion. Dropped records skip later operations.")
        .required(true).defaultValue("[]").addValidator(StandardValidators.NON_EMPTY_VALIDATOR).build();
    public static final Relationship SUCCESS = new Relationship.Builder().name("success").description("Complete transformed object or array; all original unrelated fields retained.").build();
    public static final Relationship EMPTY = new Relationship.Builder().name("empty").description("Empty object array; original content retained.").build();
    public static final Relationship FAILURE = new Relationship.Builder().name("failure").description("Original complete batch; no partial output and no payload values in diagnostics.").build();
    static final int MAX_INPUT_BYTES = 256 * 1024;
    static final int MAX_RULE_BYTES = 32 * 1024;
    static final int MAX_OUTPUT_BYTES = 2 * 1024 * 1024;
    static final int MAX_LEGACY_NUMBER_CHARS = 1024;
    private static final java.util.regex.Pattern LEGACY_NUMBER = java.util.regex.Pattern.compile("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]{1,6})?");

    private static final BoundedRhinoDateParser RHINO_DATE_PARSER = new BoundedRhinoDateParser();

    @Override public List<PropertyDescriptor> getSupportedPropertyDescriptors() { return List.of(OPERATIONS); }
    @Override public Set<Relationship> getRelationships() { return Set.of(SUCCESS, EMPTY, FAILURE); }

    @Override public void onTrigger(ProcessContext context, ProcessSession session) throws ProcessException {
        FlowFile input = session.get(); if (input == null) return;
        if (input.getSize() > MAX_INPUT_BYTES) { reject(session, input, "INPUT_LIMIT: maximum 256 KiB"); return; }
        byte[] bytes;
        try { bytes = readInput(session, input); }
        catch (IOException infrastructureError) { throw new ProcessException("Unable to read NiFi content repository", infrastructureError); }
        Result result; byte[] output; String hash;
        try {
            if (bytes.length > MAX_INPUT_BYTES) throw new IllegalArgumentException("Input limit");
            String inputJson = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            String rules = context.getProperty(OPERATIONS).getValue();
            Object parsedRules = parse(rules, MAX_RULE_BYTES);
            if (!(parsedRules instanceof List<?> list)) throw new IllegalArgumentException("Operations array required");
            var transform = new com.hm.governance.compatibility.JsonRecordTransform(list, new Codec() {
                @Override public Long legacyDateParse(String text, String zone) { return RHINO_DATE_PARSER.parse(text, zone); }
                @Override public Object parse(String text) { return JsonRecordTransform.parse(text, MAX_INPUT_BYTES); }
                @Override public Object parse(String text, boolean ecmascriptDouble) { return JsonRecordTransform.parse(text, MAX_INPUT_BYTES, ecmascriptDouble); }
                @Override public Object parse(String text, String numberMode) {
                    return numberMode.equals("KETTLE_JSON") ? parseKettleJson(text) : parse(text, numberMode.equals("ECMASCRIPT_DOUBLE"));
                }
                @Override public String kettleString(Object value) {
                    if (value instanceof String || value instanceof Number || value instanceof Boolean) return value.toString();
                    StringBuilder output = new StringBuilder(); kettleJson(value, output); return output.toString();
                }
                @Override public String stringify(Object value) { return JSON.toJSONString(value, JSONWriter.Feature.WriteMapNullValue); }
                @Override public String stringify(Object value, boolean ecmascriptDouble) {
                    if (!ecmascriptDouble) return stringify(value);
                    StringBuilder output = new StringBuilder(); ecmaJson(value, output); return output.toString();
                }
            });
            result = transform.apply(parse(inputJson, MAX_INPUT_BYTES));
            output = result.empty() ? null : JSON.toJSONBytes(result.value(), JSONWriter.Feature.WriteMapNullValue);
            if (output != null && output.length > MAX_OUTPUT_BYTES) throw new IllegalArgumentException("Output byte limit");
            hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(rules.getBytes(StandardCharsets.UTF_8)));
        } catch (BoundedRhinoDateParser.ZoneException invalidZone) {
            reject(session, input, invalidZone.getMessage()); return;
        } catch (Exception invalidBatch) {
            reject(session, input, "INVALID_TRANSFORM_BATCH: verify operations, pointers, types, date formats and limits");
            return;
        }
        input = session.putAllAttributes(input, Map.of("governance.transform.input.records", Integer.toString(result.inputRecords()),
            "governance.transform.output.records", Integer.toString(result.outputRecords()), "governance.transform.operations.sha256", hash,
            "record.count", Integer.toString(result.outputRecords())));
        if (result.empty()) { session.transfer(input, EMPTY); return; }
        // Infrastructure writes are not caught as data validation failures: NiFi must roll them back.
        input = session.write(input, stream -> stream.write(output));
        input = session.putAttribute(input, "mime.type", "application/json");
        session.transfer(input, SUCCESS);
    }
    protected byte[] readInput(ProcessSession session, FlowFile input) throws IOException {
        try (var stream = session.read(input)) { return stream.readNBytes(MAX_INPUT_BYTES + 1); }
    }
    private static void reject(ProcessSession session, FlowFile input, String message) {
        session.transfer(session.putAttribute(input, "governance.error", message), FAILURE);
    }
    private static void ecmaJson(Object value, StringBuilder out) {
        if (value == null) append(out, "null");
        else if (value instanceof String text) append(out, JSON.toJSONString(text));
        else if (value instanceof Boolean bool) append(out, bool.toString());
        else if (value instanceof Number number) append(out, ecmaNumber(number.doubleValue()));
        else if (value instanceof Map<?, ?> map) {
            append(out, "{"); boolean first = true;
            // The supplied Kettle Rhino 1.7R3 oracle retains numeric keys in insertion order.
            // Keep that explicit legacy contract rather than modern ECMAScript array-index sorting.
            for (var entry : map.entrySet()) {
                if (!first) append(out, ","); first = false; append(out, JSON.toJSONString((String) entry.getKey())); append(out, ":"); ecmaJson(entry.getValue(), out);
            }
            append(out, "}");
        } else if (value instanceof List<?> list) {
            append(out, "["); boolean first = true;
            for (Object item : list) { if (!first) append(out, ","); first = false; ecmaJson(item, out); }
            append(out, "]");
        } else throw new IllegalArgumentException("Unsupported document value");
    }
    private static void append(StringBuilder output, String text) {
        if ((long) output.length() + text.length() > MAX_OUTPUT_BYTES) throw new IllegalArgumentException("Serialized document limit");
        output.append(text);
    }
    static String ecmaNumber(double value) {
        if (!Double.isFinite(value)) return "null";
        if (value == 0) return "0";
        boolean negative = value < 0; double positive = Math.abs(value);
        java.math.BigDecimal exact = new java.math.BigDecimal(positive), chosen = null;
        // Find the shortest decimal that round-trips to this IEEE-754 value, then the nearest/even candidate.
        // This also handles one-digit subnormals for which Double.toString uses an extra digit.
        for (int precision = 1; precision <= 17 && chosen == null; precision++) {
            var rounded = exact.round(new java.math.MathContext(precision, java.math.RoundingMode.HALF_EVEN));
            var unit = java.math.BigDecimal.ONE.scaleByPowerOfTen(exact.precision() - exact.scale() - precision);
            java.math.BigDecimal distance = null;
            for (var candidate : List.of(rounded, rounded.subtract(unit), rounded.add(unit))) {
                if (candidate.signum() <= 0 || Double.doubleToLongBits(candidate.doubleValue()) != Double.doubleToLongBits(positive)) continue;
                var delta = candidate.subtract(exact).abs();
                if (chosen == null || delta.compareTo(distance) < 0 || delta.compareTo(distance) == 0 && !candidate.unscaledValue().testBit(0)) {
                    chosen = candidate.stripTrailingZeros(); distance = delta;
                }
            }
        }
        if (chosen == null) throw new IllegalArgumentException("Number conversion failed");
        String digits;
        if (positive >= 1e-6 && positive < 1e21) digits = chosen.toPlainString();
        else {
            String coefficient = chosen.unscaledValue().toString(); int exponent = chosen.precision() - chosen.scale() - 1;
            digits = coefficient.charAt(0) + (coefficient.length() > 1 ? "." + coefficient.substring(1) : "") + "e" + (exponent >= 0 ? "+" : "") + exponent;
        }
        return (negative ? "-" : "") + digits;
    }
    private static String normalizeLegacyNumbers(String text) { return normalizeNumbers(text, null); }
    private static String normalizeNumbers(String text, List<Number> kettleNumbers) {
        StringBuilder result = new StringBuilder(); boolean quoted = false;
        for (int i = 0; i < text.length();) {
            char c = text.charAt(i);
            if (quoted) {
                result.append(c); i++;
                if (c == '\\') {
                    if (i == text.length()) throw new IllegalArgumentException("Invalid JSON string");
                    result.append(text.charAt(i++));
                } else if (c == '"') quoted = false;
            } else if (c == '"') { quoted = true; result.append(c); i++; }
            else if (c == '-' || c >= '0' && c <= '9') {
                if (i > 0 && "[:,{ \t\r\n".indexOf(text.charAt(i - 1)) < 0) throw new IllegalArgumentException("Invalid numeric token");
                int end = i;
                while (end < text.length() && ",]} \t\r\n".indexOf(text.charAt(end)) < 0) {
                    if (end - i >= MAX_LEGACY_NUMBER_CHARS) throw new IllegalArgumentException("Legacy numeric token limit");
                    end++;
                }
                String token = text.substring(i, end);
                if (!LEGACY_NUMBER.matcher(token).matches()) throw new IllegalArgumentException("Invalid or oversized legacy number");
                if (kettleNumbers != null) {
                    if (kettleNumbers.size() >= com.hm.governance.compatibility.JsonRecordTransform.MAX_VALUES) throw new IllegalArgumentException("Numeric value count limit");
                    kettleNumbers.add(kettleNumber(token)); append(result, "0");
                } else {
                    double number = Double.parseDouble(token);
                    // Infinity stays in temporary documents; JSON serialization alone maps it to null.
                    String normalized = Double.isInfinite(number) ? number > 0 ? "1e400" : "-1e400" : Double.toString(number);
                    if (normalized.endsWith(".0")) normalized = normalized.substring(0, normalized.length() - 2);
                    append(result, normalized);
                }
                i = end;
            } else {
                if (kettleNumbers != null && "{}[],: \t\r\n".indexOf(c) < 0) {
                    String literal = c == 't' ? "true" : c == 'f' ? "false" : c == 'n' ? "null" : "";
                    if (literal.isEmpty() || !text.startsWith(literal, i)) throw new IllegalArgumentException("Strict JSON token required");
                    append(result, literal); i += literal.length();
                } else { result.append(c); i++; }
            }
            if (result.length() > MAX_OUTPUT_BYTES) throw new IllegalArgumentException("Normalized document limit");
        }
        return result.toString();
    }
    // JsonSmart 2.2's observed provider keeps integers exact and switches decimal tokens at 18 characters.
    private static Number kettleNumber(String token) {
        if (token.indexOf('.') >= 0 || token.indexOf('e') >= 0 || token.indexOf('E') >= 0)
            return token.length() > 18 ? new java.math.BigDecimal(token) : Double.valueOf(token);
        try { return Long.valueOf(token); }
        catch (NumberFormatException outsideLong) { return new java.math.BigInteger(token); }
    }
    private static Object parseKettleJson(String text) {
        if (text.length() > MAX_INPUT_BYTES || text.getBytes(StandardCharsets.UTF_8).length > MAX_INPUT_BYTES) throw new IllegalArgumentException("JSON byte limit");
        List<Number> numbers = new java.util.ArrayList<>();
        // Zero placeholders preserve JSON structure and insertion order. Restore from the bounded lexical
        // queue after parsing, so neither exponent limits nor precision-losing reparsing affect Kettle numbers.
        Object parsed = readJson(normalizeNumbers(text, numbers), false);
        var iterator = numbers.iterator(); parsed = restoreKettleNumbers(parsed, iterator);
        if (iterator.hasNext()) throw new IllegalArgumentException("Numeric token count mismatch");
        return parsed;
    }
    @SuppressWarnings("unchecked") private static Object restoreKettleNumbers(Object value, java.util.Iterator<Number> numbers) {
        if (value instanceof Number) {
            if (!numbers.hasNext()) throw new IllegalArgumentException("Numeric token count mismatch");
            return numbers.next();
        }
        if (value instanceof Map<?, ?> map) ((Map<String, Object>) map).replaceAll((key, child) -> restoreKettleNumbers(child, numbers));
        else if (value instanceof List<?> list) {
            List<Object> items = (List<Object>) list;
            for (int i = 0; i < items.size(); i++) items.set(i, restoreKettleNumbers(items.get(i), numbers));
        }
        return value;
    }
    private static void kettleJson(Object value, StringBuilder out) {
        if (value == null) append(out, "null");
        else if (value instanceof String text) kettleQuote(text, out);
        else if (value instanceof Number number) {
            boolean nonfinite = number instanceof Double d && !Double.isFinite(d) || number instanceof Float f && !Float.isFinite(f);
            append(out, nonfinite ? "null" : number.toString());
        } else if (value instanceof Boolean flag) append(out, flag.toString());
        else if (value instanceof Map<?, ?> map) {
            append(out, "{"); boolean first = true;
            for (var entry : map.entrySet()) {
                if (!first) append(out, ","); first = false;
                kettleQuote((String) entry.getKey(), out); append(out, ":"); kettleJson(entry.getValue(), out);
            }
            append(out, "}");
        } else if (value instanceof List<?> list) {
            append(out, "["); boolean first = true;
            for (Object child : list) { if (!first) append(out, ","); first = false; kettleJson(child, out); }
            append(out, "]");
        } else throw new IllegalArgumentException("Unsupported Kettle string value");
    }
    private static void kettleQuote(String text, StringBuilder out) {
        append(out, "\"");
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            String escaped = switch (c) {
                case '"' -> "\\\""; case '\\' -> "\\\\"; case '/' -> "\\/";
                case '\b' -> "\\b"; case '\f' -> "\\f"; case '\n' -> "\\n"; case '\r' -> "\\r"; case '\t' -> "\\t";
                default -> c <= 0x1f || c >= 0x7f && c <= 0x9f || c >= 0x2000 && c <= 0x20ff
                    ? "\\u" + String.format(java.util.Locale.ROOT, "%04X", (int) c) : String.valueOf(c);
            };
            append(out, escaped);
        }
        append(out, "\"");
    }
    private static Object parse(String text, int byteLimit) { return parse(text, byteLimit, false); }
    private static Object parse(String text, int byteLimit, boolean ecmascriptDouble) {
        if (text == null || text.length() > byteLimit || text.getBytes(StandardCharsets.UTF_8).length > byteLimit) throw new IllegalArgumentException("JSON byte limit");
        // Fastjson caps exponent magnitude even with UseDoubleForDecimals. Normalize bounded numeric
        // tokens through Double first, so 1e-4000 becomes zero without allocating a huge BigDecimal.
        if (ecmascriptDouble) text = normalizeLegacyNumbers(text);
        return readJson(text, ecmascriptDouble);
    }
    private static Object readJson(String text, boolean ecmascriptDouble) {
        var context = ecmascriptDouble
            ? new JSONReader.Context(JSONReader.Feature.UseDoubleForDecimals, JSONReader.Feature.DisableReferenceDetect, JSONReader.Feature.DisableSingleQuote)
            : new JSONReader.Context(JSONReader.Feature.UseBigDecimalForDoubles, JSONReader.Feature.UseBigDecimalForFloats,
                JSONReader.Feature.DisableReferenceDetect, JSONReader.Feature.DisableSingleQuote);
        context.setMaxLevel(com.hm.governance.compatibility.JsonRecordTransform.MAX_DEPTH);
        context.setObjectSupplier(() -> new LinkedHashMap<String, Object>() {
            @Override public Object put(String key, Object value) {
                if (containsKey(key)) throw new IllegalArgumentException("Duplicate JSON key");
                return super.put(key, value);
            }
        });
        try (var reader = JSONReader.of(text, context)) {
            Object value = reader.readAny();
            if (!reader.isEnd()) throw new IllegalArgumentException("Trailing JSON content");
            return value;
        }
    }
}
