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
@CapabilityDescription("Transforms a JSON object or object array using 1 to 64 explicit data operations, including row filtering. No scripting, expressions, external I/O or regex. All retained records succeed together or the original batch is rejected. parse defaults to EXACT numbers; opt-in ECMASCRIPT_DOUBLE loses numeric precision like JavaScript and serializes nonfinite document numbers as null. dateGate uses strict Java dates and an explicit zone, with FAIL or FALSE for invalid text; it does not reproduce every permissive JavaScript Date.parse form.")
@InputRequirement(InputRequirement.Requirement.INPUT_REQUIRED)
public class JsonRecordTransform extends AbstractProcessor {
    public static final PropertyDescriptor OPERATIONS = new PropertyDescriptor.Builder().name("Operations")
        .description("JSON array, 1 to 64 operations and maximum 32 KiB. Ops: parse, get, constant, copy, remove, trim, replace, substring, set, broadcast, serialize, dateGate, filter. remove deletes only its explicit root output field. Input and path are strict JSON Pointers. STRING is a strict type check, not numeric coercion. Trimming removes characters <= U+0020. replace is literal with FIRST or ALL; null string values remain null. get missing defaults to FAIL, optionally NULL. set/broadcast may create an object leaf but never missing intermediate containers or array slots. broadcast outerIndex=true substitutes a complete $index path token. parse numberMode defaults EXACT; ECMASCRIPT_DOUBLE explicitly loses precision. dateGate onInvalid defaults FAIL, optionally FALSE; comparison is strict greater-than. filter supports scalar EQ/NE/IS_NULL/IS_NOT_NULL and keep=true(default)/false(invert). Missing is distinct from null and matches no predicate; numeric comparison is exact decimal, with no string/boolean coercion. Dropped records skip later operations.")
        .required(true).defaultValue("[]").addValidator(StandardValidators.NON_EMPTY_VALIDATOR).build();
    public static final Relationship SUCCESS = new Relationship.Builder().name("success").description("Complete transformed object or array; all original unrelated fields retained.").build();
    public static final Relationship EMPTY = new Relationship.Builder().name("empty").description("Empty object array; original content retained.").build();
    public static final Relationship FAILURE = new Relationship.Builder().name("failure").description("Original complete batch; no partial output and no payload values in diagnostics.").build();
    static final int MAX_INPUT_BYTES = 256 * 1024;
    static final int MAX_RULE_BYTES = 32 * 1024;
    static final int MAX_OUTPUT_BYTES = 2 * 1024 * 1024;

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
                @Override public Object parse(String text) { return JsonRecordTransform.parse(text, MAX_INPUT_BYTES); }
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
            // ECMAScript enumerates canonical array-index keys first, then other keys in insertion order.
            List<String> keys = new java.util.ArrayList<>(); for (Object key : map.keySet()) keys.add((String) key);
            keys.sort((a, b) -> { long ai = arrayKey(a), bi = arrayKey(b); return ai >= 0 && bi >= 0 ? Long.compare(ai, bi) : ai >= 0 ? -1 : bi >= 0 ? 1 : 0; });
            for (String key : keys) {
                if (!first) append(out, ","); first = false; append(out, JSON.toJSONString(key)); append(out, ":"); ecmaJson(map.get(key), out);
            }
            append(out, "}");
        } else if (value instanceof List<?> list) {
            append(out, "["); boolean first = true;
            for (Object item : list) { if (!first) append(out, ","); first = false; ecmaJson(item, out); }
            append(out, "]");
        } else throw new IllegalArgumentException("Unsupported document value");
    }
    private static long arrayKey(String key) {
        if (!key.matches("0|[1-9][0-9]{0,9}")) return -1;
        long number = Long.parseLong(key); return number < 4294967295L ? number : -1;
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
    private static Object parse(String text, int byteLimit) {
        if (text == null || text.length() > byteLimit || text.getBytes(StandardCharsets.UTF_8).length > byteLimit) throw new IllegalArgumentException("JSON byte limit");
        var context = new JSONReader.Context(JSONReader.Feature.UseBigDecimalForDoubles, JSONReader.Feature.UseBigDecimalForFloats,
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
