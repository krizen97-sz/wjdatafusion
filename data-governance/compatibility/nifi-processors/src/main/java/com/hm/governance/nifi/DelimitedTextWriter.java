package com.hm.governance.nifi;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.hm.governance.compatibility.DelimitedBatchEncoder;
import com.hm.governance.compatibility.DelimitedBatchEncoder.CountBasis;
import com.hm.governance.compatibility.DelimitedBatchEncoder.Options;
import com.hm.governance.compatibility.DelimitedBatchEncoder.Row;
import com.hm.governance.compatibility.DelimitedBatchEncoder.Segment;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
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

@Tags({"governance", "text", "delimiter", "batch", "kettle"})
@CapabilityDescription("Encodes a closed JSON object-array batch as UTF-8 delimited FlowFiles. Supports data-record counts or the audited Kettle header-inclusive counter. No network or filesystem side effects. Age rolling requires explicit monotonic arrival timestamps; this is not a streaming batch collector or FTP acknowledgement.")
@InputRequirement(InputRequirement.Requirement.INPUT_REQUIRED)
public class DelimitedTextWriter extends AbstractProcessor {
    public static final PropertyDescriptor FIELDS = property("Field Order", "Comma-separated object field names, in wire order.", null, true);
    public static final PropertyDescriptor DELIMITER = property("Delimiter Hex", "One to 32 bytes in hexadecimal, for example 7C 1F.", "7C 1F", true);
    public static final PropertyDescriptor HEADER = new PropertyDescriptor.Builder().name("Include Header").description("Write exactly one header per segment.").required(true).defaultValue("true").allowableValues("true", "false").build();
    public static final PropertyDescriptor LIMIT = new PropertyDescriptor.Builder().name("Split Limit").description("Zero disables count splitting. Legacy mode includes header writes in its global counter.").required(true).defaultValue("75").addValidator(StandardValidators.NON_NEGATIVE_INTEGER_VALIDATOR).build();
    public static final PropertyDescriptor BASIS = new PropertyDescriptor.Builder().name("Count Basis").description("Choose explicit record counting or audited legacy header-inclusive counting.").required(true).defaultValue(CountBasis.DATA_RECORDS.name()).allowableValues(CountBasis.DATA_RECORDS.name(), CountBasis.KETTLE_HEADER_INCLUSIVE.name()).build();
    public static final PropertyDescriptor AGE = new PropertyDescriptor.Builder().name("Maximum File Age Millis").description("Rolling checked on the next record, strictly after this age. Nonzero requires Arrival Time Field.").required(true).defaultValue("0").addValidator(StandardValidators.NON_NEGATIVE_INTEGER_VALIDATOR).build();
    public static final PropertyDescriptor ARRIVAL = property("Arrival Time Field", "Optional record field holding nonnegative epoch milliseconds. Required when age rolling is enabled.", null, false);
    public static final PropertyDescriptor PREFIX = property("Filename Prefix", "Safe file basename prefix. No directory paths.", "dataset", true);
    public static final Relationship SUCCESS = new Relationship.Builder().name("success").description("All encoded segments.").build();
    public static final Relationship FAILURE = new Relationship.Builder().name("failure").description("Original input rejected without exposing payload in diagnostics.").build();
    public static final Relationship EMPTY = new Relationship.Builder().name("empty").description("Closed batch contains zero records; no header-only file produced.").build();
    private static final int MAX_INPUT_BYTES = 2 * 1024 * 1024;
    private static PropertyDescriptor property(String name, String description, String value, boolean required) {
        var b = new PropertyDescriptor.Builder().name(name).description(description).required(required).addValidator(StandardValidators.NON_EMPTY_VALIDATOR);
        if (value != null) b.defaultValue(value);
        return b.build();
    }
    @Override public List<PropertyDescriptor> getSupportedPropertyDescriptors() { return List.of(FIELDS, DELIMITER, HEADER, LIMIT, BASIS, AGE, ARRIVAL, PREFIX); }
    @Override public Set<Relationship> getRelationships() { return Set.of(SUCCESS, FAILURE, EMPTY); }

    @Override public void onTrigger(ProcessContext context, ProcessSession session) throws ProcessException {
        FlowFile input = session.get();
        if (input == null) return;
        if (input.getSize() > MAX_INPUT_BYTES) {
            session.transfer(session.putAttribute(input, "governance.error", "INPUT_LIMIT: maximum 2 MiB"), FAILURE);
            return;
        }
        byte[] bytes;
        try {
            bytes = readInput(session, input);
        } catch (IOException infrastructureError) {
            throw new ProcessException("Unable to read NiFi content repository", infrastructureError);
        }
        List<Segment> segments;
        String prefix;
        try {
            if (bytes.length > MAX_INPUT_BYTES) throw new IllegalArgumentException("Input limit");
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            JSONArray records = JSON.parseArray(text);
            if (records == null || records.size() > 10000) throw new IllegalArgumentException("Batch limit");
            List<String> fields = Arrays.stream(context.getProperty(FIELDS).getValue().split(",", -1)).map(String::trim).toList();
            if (fields.stream().distinct().count() != fields.size()) throw new IllegalArgumentException("Duplicate fields");
            long age = context.getProperty(AGE).asLong();
            String arrivalField = context.getProperty(ARRIVAL).getValue();
            if (age > 0 && (arrivalField == null || arrivalField.isBlank())) throw new IllegalArgumentException("Arrival field required");
            List<Row> rows = new ArrayList<>();
            for (Object raw : records) {
                if (!(raw instanceof JSONObject record)) throw new IllegalArgumentException("Object records required");
                List<String> values = new ArrayList<>();
                for (String field : fields) {
                    if (!record.containsKey(field)) throw new IllegalArgumentException("Missing field");
                    Object value = record.get(field);
                    values.add(value == null ? null : value instanceof String s ? s : JSON.toJSONString(value));
                }
                long arrival = 0;
                if (arrivalField != null && !arrivalField.isBlank()) {
                    if (!(record.get(arrivalField) instanceof Number number)) throw new IllegalArgumentException("Numeric arrival required");
                    arrival = new BigDecimal(number.toString()).longValueExact();
                    if (arrival < 0) throw new IllegalArgumentException("Negative arrival");
                }
                rows.add(new Row(values, arrival));
            }
            prefix = context.getProperty(PREFIX).getValue();
            if (!prefix.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,63}")) throw new IllegalArgumentException("Unsafe prefix");
            Options options = new Options(fields, context.getProperty(DELIMITER).getValue(), context.getProperty(HEADER).asBoolean(), context.getProperty(LIMIT).asInteger(), CountBasis.valueOf(context.getProperty(BASIS).getValue()), age, "\n");
            segments = new DelimitedBatchEncoder().encode(rows, options, 8L * 1024 * 1024);
        } catch (Exception error) {
            input = session.putAttribute(input, "governance.error", "INVALID_BATCH: verify object-array input, fields, limits and timestamps");
            session.transfer(input, FAILURE);
            return;
        }
        if (segments.isEmpty()) {
            input = session.putAttribute(input, "record.count", "0");
            session.transfer(input, EMPTY);
            return;
        }
        String batch = input.getAttribute("uuid");
        for (Segment segment : segments) {
            FlowFile output = session.create(input);
            output = session.write(output, stream -> stream.write(segment.content()));
            output = session.putAllAttributes(output, Map.of(
                "filename", prefix + "_" + batch + "_" + segment.index() + ".csv",
                "mime.type", "text/plain; charset=UTF-8",
                "record.count", Integer.toString(segment.records()),
                "governance.writer.lines", Integer.toString(segment.writerLines()),
                "fragment.identifier", batch,
                "fragment.index", Integer.toString(segment.index()),
                "fragment.count", Integer.toString(segments.size())));
            session.transfer(output, SUCCESS);
        }
        session.remove(input);
    }

    protected byte[] readInput(ProcessSession session, FlowFile input) throws IOException {
        try (var stream = session.read(input)) { return stream.readNBytes(MAX_INPUT_BYTES + 1); }
    }
}
