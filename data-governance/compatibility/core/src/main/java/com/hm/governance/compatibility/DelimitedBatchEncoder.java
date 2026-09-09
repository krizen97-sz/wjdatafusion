package com.hm.governance.compatibility;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/** Pure, bounded batch encoding. No files, credentials, network, or hidden clock. */
public final class DelimitedBatchEncoder {
    public enum CountBasis { DATA_RECORDS, KETTLE_HEADER_INCLUSIVE }

    public record Row(List<String> values, long arrivalMillis) {
        public Row {
            Objects.requireNonNull(values, "values");
            if (arrivalMillis < 0) throw new IllegalArgumentException("Arrival time must be nonnegative");
            // Null is a meaningful empty field in the legacy wire format.
            values = java.util.Collections.unmodifiableList(new ArrayList<>(values));
        }
    }

    public record Options(List<String> fields, String delimiterHex, boolean header,
                          int splitLimit, CountBasis countBasis, long maxFileAgeMillis,
                          String newline) {
        public Options {
            fields = List.copyOf(fields);
            if (fields.isEmpty() || fields.size() > 256) throw new IllegalArgumentException("1..256 fields required");
            if (fields.stream().anyMatch(f -> f == null || f.isBlank() || f.contains("\n") || f.contains("\r")))
                throw new IllegalArgumentException("Invalid field name");
            parseDelimiter(delimiterHex);
            Objects.requireNonNull(countBasis, "countBasis");
            if (splitLimit < 0 || splitLimit > 1_000_000) throw new IllegalArgumentException("Invalid split limit");
            if (header && countBasis == CountBasis.KETTLE_HEADER_INCLUSIVE && splitLimit == 1)
                throw new IllegalArgumentException("Header-inclusive limit must be zero or at least two");
            if (maxFileAgeMillis < 0) throw new IllegalArgumentException("Negative file age");
            if (!"\n".equals(newline) && !"\r\n".equals(newline)) throw new IllegalArgumentException("LF or CRLF required");
        }
    }

    public record Segment(int index, int records, int writerLines, long firstArrivalMillis,
                          long lastArrivalMillis, byte[] content) {
        public Segment { content = content.clone(); }
        @Override public byte[] content() { return content.clone(); }
    }

    public static byte[] parseDelimiter(String hex) {
        if (hex == null) throw new IllegalArgumentException("Delimiter hex required");
        String compact = hex.replace(" ", "");
        if (compact.isEmpty() || compact.length() > 64 || (compact.length() & 1) != 0 || !compact.matches("[0-9a-fA-F]+"))
            throw new IllegalArgumentException("Delimiter must be 1..32 bytes of hexadecimal");
        return HexFormat.of().parseHex(compact);
    }

    /** Caller must supply a closed batch and monotonic arrival times. Idle time alone emits nothing. */
    public List<Segment> encode(List<Row> rows, Options options, long maxOutputBytes) {
        Objects.requireNonNull(rows, "rows");
        Objects.requireNonNull(options, "options");
        if (rows.size() > 100_000 || maxOutputBytes < 1 || maxOutputBytes > 128L * 1024 * 1024)
            throw new IllegalArgumentException("Batch size or byte budget out of bounds");
        long previous = Long.MIN_VALUE;
        for (Row row : rows) {
            if (row.values().size() != options.fields().size()) throw new IllegalArgumentException("Field count mismatch");
            if (row.arrivalMillis() < previous) throw new IllegalArgumentException("Arrival time must be monotonic");
            previous = row.arrivalMillis();
        }
        if (rows.isEmpty()) return List.of();
        byte[] delimiter = parseDelimiter(options.delimiterHex());
        byte[] newline = options.newline().getBytes(StandardCharsets.UTF_8);
        List<Segment> result = new ArrayList<>();
        ByteArrayOutputStream current = new ByteArrayOutputStream();
        long bytesWritten = 0;
        long globalWriterLines = 0;
        int records = 0;
        long first = rows.get(0).arrivalMillis();
        long last = first;
        if (options.header()) {
            bytesWritten += writeLine(current, options.fields(), delimiter, newline, maxOutputBytes - bytesWritten);
            globalWriterLines++;
        }
        for (Row row : rows) {
            boolean countReached = options.splitLimit() > 0 && (options.countBasis() == CountBasis.DATA_RECORDS
                ? records >= options.splitLimit() : globalWriterLines % options.splitLimit() == 0);
            boolean ageReached = options.maxFileAgeMillis() > 0 && row.arrivalMillis() - first > options.maxFileAgeMillis();
            if (records > 0 && (countReached || ageReached)) {
                result.add(new Segment(result.size(), records, records + (options.header() ? 1 : 0), first, last, current.toByteArray()));
                current = new ByteArrayOutputStream();
                records = 0;
                first = row.arrivalMillis();
                if (options.header()) {
                    bytesWritten += writeLine(current, options.fields(), delimiter, newline, maxOutputBytes - bytesWritten);
                    globalWriterLines++;
                }
            }
            bytesWritten += writeLine(current, row.values(), delimiter, newline, maxOutputBytes - bytesWritten);
            globalWriterLines++;
            records++;
            last = row.arrivalMillis();
        }
        result.add(new Segment(result.size(), records, records + (options.header() ? 1 : 0), first, last, current.toByteArray()));
        return List.copyOf(result);
    }

    private static int writeLine(ByteArrayOutputStream target, List<String> values, byte[] delimiter,
                                 byte[] newline, long remaining) {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) line.writeBytes(delimiter);
            if (values.get(i) != null) {
                if (values.get(i).length() > remaining - line.size()) throw new IllegalArgumentException("Output byte budget exceeded");
                line.writeBytes(values.get(i).getBytes(StandardCharsets.UTF_8));
            }
            if (line.size() > remaining) throw new IllegalArgumentException("Output byte budget exceeded");
        }
        line.writeBytes(newline);
        if (line.size() > remaining) throw new IllegalArgumentException("Output byte budget exceeded");
        target.writeBytes(line.toByteArray());
        return line.size();
    }
}
