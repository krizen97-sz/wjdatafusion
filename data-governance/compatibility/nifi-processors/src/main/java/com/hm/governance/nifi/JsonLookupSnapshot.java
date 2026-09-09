package com.hm.governance.nifi;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONWriter;
import com.hm.governance.compatibility.LookupSnapshot;
import com.hm.governance.compatibility.LookupSnapshot.Match;
import com.hm.governance.compatibility.LookupSnapshot.MissingMatch;
import com.hm.governance.compatibility.LookupSnapshot.MultipleMatches;
import com.hm.governance.compatibility.LookupSnapshot.Operator;
import com.hm.governance.compatibility.LookupSnapshot.ReturnField;
import com.hm.governance.compatibility.LookupSnapshot.ValueType;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.LinkedHashMap;
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

@Tags({"governance", "json", "lookup", "snapshot", "dictionary"})
@CapabilityDescription("Enriches a JSON object or object array using an explicit bounded JSON dictionary snapshot. Multiple conditions match the same dictionary row. Exact strings or decimal numbers, explicit missing and multiple-match policies, and whole-batch validation. No network or filesystem side effects; this processor does not refresh a database or reproduce unordered database selection.")
@InputRequirement(InputRequirement.Requirement.INPUT_REQUIRED)
public class JsonLookupSnapshot extends AbstractProcessor {
    public static final PropertyDescriptor LOOKUP_ROWS = json("Lookup Rows", "JSON object array, maximum 1000 rows and 256 KiB. Snapshot is fixed until explicitly replaced.");
    public static final PropertyDescriptor MATCH_FIELDS = json("Match Fields", "1 to 64 rules: {input: JSON Pointer, lookup: column, type: STRING or NUMBER, operator: EQ or IS_NOT_NULL}. IS_NOT_NULL omits input. STRING requires exact strings; NUMBER accepts JSON numbers or strict decimal strings.");
    public static final PropertyDescriptor RETURN_FIELDS = json("Return Fields", "1 to 64 rules: {lookup: column, output: plain root field name, default: JSON value}. Default applies only to unmatched KEEP records; omitted means null. Matched missing columns fail the whole batch.");
    public static final PropertyDescriptor MISSING_MATCH = new PropertyDescriptor.Builder().name("Missing Match").description("KEEP enriches with defaults or null; DROP omits unmatched records; FAIL rejects the whole batch.")
        .required(true).defaultValue("KEEP").allowableValues("KEEP", "DROP", "FAIL").build();
    public static final PropertyDescriptor MULTIPLE_MATCHES = new PropertyDescriptor.Builder().name("Multiple Matches").description("FAIL rejects ambiguity. FIRST and LAST use only the explicitly supplied snapshot row order, not an assumed database order.")
        .required(true).defaultValue("FAIL").allowableValues("FAIL", "FIRST", "LAST").build();
    public static final Relationship SUCCESS = new Relationship.Builder().name("success").description("Complete enriched object or array; all records validated before writing.").build();
    public static final Relationship EMPTY = new Relationship.Builder().name("empty").description("No output records: an empty input array or all records dropped. Original input is retained.").build();
    public static final Relationship FAILURE = new Relationship.Builder().name("failure").description("Original batch rejected; diagnostics never include payload values.").build();
    static final int MAX_INPUT_BYTES = 256 * 1024;
    static final int MAX_RULE_BYTES = 32 * 1024;
    static final int MAX_OUTPUT_BYTES = 2 * 1024 * 1024;

    private static PropertyDescriptor json(String name, String description) {
        return new PropertyDescriptor.Builder().name(name).description(description).required(true).defaultValue("[]")
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR).build();
    }
    @Override public List<PropertyDescriptor> getSupportedPropertyDescriptors() { return List.of(LOOKUP_ROWS, MATCH_FIELDS, RETURN_FIELDS, MISSING_MATCH, MULTIPLE_MATCHES); }
    @Override public Set<Relationship> getRelationships() { return Set.of(SUCCESS, EMPTY, FAILURE); }

    @Override public void onTrigger(ProcessContext context, ProcessSession session) throws ProcessException {
        FlowFile input = session.get();
        if (input == null) return;
        if (input.getSize() > MAX_INPUT_BYTES) { reject(session, input, "INPUT_LIMIT: maximum 256 KiB"); return; }
        byte[] bytes;
        try { bytes = readInput(session, input); }
        catch (IOException infrastructureError) { throw new ProcessException("Unable to read NiFi content repository", infrastructureError); }
        LookupSnapshot.Result result;
        byte[] output;
        String snapshotHash;
        try {
            if (bytes.length > MAX_INPUT_BYTES) throw new IllegalArgumentException("Input limit");
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            String snapshot = context.getProperty(LOOKUP_ROWS).getValue();
            List<?> rows = list(parse(snapshot, MAX_INPUT_BYTES));
            List<Match> matches = matches(list(parse(context.getProperty(MATCH_FIELDS).getValue(), MAX_RULE_BYTES)));
            List<ReturnField> returns = returns(list(parse(context.getProperty(RETURN_FIELDS).getValue(), MAX_RULE_BYTES)));
            LookupSnapshot lookup = new LookupSnapshot(rows, matches, returns,
                MissingMatch.valueOf(context.getProperty(MISSING_MATCH).getValue()),
                MultipleMatches.valueOf(context.getProperty(MULTIPLE_MATCHES).getValue()));
            result = lookup.apply(parse(text, MAX_INPUT_BYTES));
            output = result.empty() ? null : JSON.toJSONBytes(result.value(), JSONWriter.Feature.WriteMapNullValue);
            if (output != null && output.length > MAX_OUTPUT_BYTES) throw new IllegalArgumentException("Output limit");
            snapshotHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(snapshot.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception invalidBatch) {
            reject(session, input, "INVALID_LOOKUP_BATCH: verify input, snapshot, rules, types and match policies");
            return;
        }
        input = session.putAllAttributes(input, Map.of(
            "governance.lookup.input.records", Integer.toString(result.inputRecords()),
            "governance.lookup.output.records", Integer.toString(result.outputRecords()),
            "governance.lookup.matched.records", Integer.toString(result.matchedRecords()),
            "governance.lookup.unmatched.records", Integer.toString(result.unmatchedRecords()),
            "governance.lookup.snapshot.sha256", snapshotHash));
        if (result.empty()) { session.transfer(session.putAttribute(input, "record.count", "0"), EMPTY); return; }
        // Repository writes stay outside validation catches so NiFi can roll back infrastructure failures.
        input = session.write(input, stream -> stream.write(output));
        input = session.putAllAttributes(input, Map.of("mime.type", "application/json", "record.count", Integer.toString(result.outputRecords())));
        session.transfer(input, SUCCESS);
    }

    protected byte[] readInput(ProcessSession session, FlowFile input) throws IOException {
        try (var stream = session.read(input)) { return stream.readNBytes(MAX_INPUT_BYTES + 1); }
    }
    private static void reject(ProcessSession session, FlowFile input, String code) {
        session.transfer(session.putAttribute(input, "governance.error", code), FAILURE);
    }
    private static Object parse(String text, int byteLimit) {
        if (text == null || text.length() > byteLimit || text.getBytes(StandardCharsets.UTF_8).length > byteLimit) throw new IllegalArgumentException("JSON byte limit");
        var context = new JSONReader.Context(JSONReader.Feature.UseBigDecimalForDoubles, JSONReader.Feature.UseBigDecimalForFloats,
            JSONReader.Feature.DisableReferenceDetect, JSONReader.Feature.DisableSingleQuote);
        context.setMaxLevel(LookupSnapshot.MAX_DEPTH);
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
    private static List<?> list(Object value) {
        if (!(value instanceof List<?> list)) throw new IllegalArgumentException("Array required");
        return list;
    }
    private static Map<?, ?> rule(Object value, Set<String> keys) {
        if (!(value instanceof Map<?, ?> map) || !keys.containsAll(map.keySet())) throw new IllegalArgumentException("Invalid rule keys");
        return map;
    }
    private static String string(Map<?, ?> rule, String key) {
        if (!(rule.get(key) instanceof String text)) throw new IllegalArgumentException("String rule field required");
        return text;
    }
    private static List<Match> matches(List<?> raw) {
        if (raw.size() > LookupSnapshot.MAX_FIELDS) throw new IllegalArgumentException("Match limit");
        List<Match> result = new ArrayList<>();
        for (Object value : raw) {
            Map<?, ?> rule = rule(value, Set.of("input", "lookup", "type", "operator"));
            Operator operator = Operator.valueOf(string(rule, "operator"));
            String pointer = rule.containsKey("input") ? string(rule, "input") : null;
            result.add(new Match(pointer, string(rule, "lookup"), ValueType.valueOf(string(rule, "type")), operator));
        }
        return result;
    }
    private static List<ReturnField> returns(List<?> raw) {
        if (raw.size() > LookupSnapshot.MAX_FIELDS) throw new IllegalArgumentException("Return limit");
        List<ReturnField> result = new ArrayList<>();
        for (Object value : raw) {
            Map<?, ?> rule = rule(value, Set.of("lookup", "output", "default"));
            result.add(new ReturnField(string(rule, "lookup"), string(rule, "output"), rule.get("default")));
        }
        return result;
    }
}
