package com.payops.reconciliation.domain;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal streaming RFC 4180 reader: quoted fields, escaped quotes, CRLF and
 * embedded newlines. Holds one record in memory at a time and bounds field
 * and record sizes so a malformed file (e.g. an unclosed quote) cannot
 * exhaust memory.
 */
public final class SettlementCsvReader {

    private static final int MAX_FIELD_LENGTH = 2_000;
    private static final int MAX_FIELDS = 200;
    private static final int NONE = -2;

    private final Reader reader;
    private final char delimiter;
    private int pushback = NONE;
    private boolean first = true;
    private long recordNumber;

    public SettlementCsvReader(Reader reader, char delimiter) {
        this.reader = reader;
        this.delimiter = delimiter;
    }

    /** 1-based physical record number of the last record returned (header = 1). */
    public long recordNumber() {
        return recordNumber;
    }

    /** Next record, or {@code null} at end of input. Blank lines are skipped. */
    public List<String> next() {
        while (true) {
            List<String> record = readRecord();
            if (record == null) {
                return null;
            }
            recordNumber++;
            if (!(record.size() == 1 && record.get(0).isBlank())) {
                return record;
            }
        }
    }

    private List<String> readRecord() {
        int c = read();
        if (c == -1) {
            return null;
        }
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        boolean fieldStart = true;
        while (true) {
            if (c == -1) {
                if (quoted) {
                    throw new IllegalArgumentException("unterminated quoted field");
                }
                addField(fields, field);
                return fields;
            }
            char ch = (char) c;
            if (quoted) {
                if (ch == '"') {
                    int peek = read();
                    if (peek == '"') {
                        append(field, '"');
                    } else {
                        quoted = false;
                        c = peek;
                        continue;
                    }
                } else {
                    append(field, ch);
                }
            } else if (ch == '"' && fieldStart) {
                quoted = true;
                fieldStart = false;
            } else if (ch == delimiter) {
                addField(fields, field);
                field.setLength(0);
                fieldStart = true;
            } else if (ch == '\r' || ch == '\n') {
                if (ch == '\r') {
                    int peek = read();
                    if (peek != '\n') {
                        pushback = peek;
                    }
                }
                addField(fields, field);
                return fields;
            } else {
                append(field, ch);
                fieldStart = false;
            }
            c = read();
        }
    }

    private static void addField(List<String> fields, StringBuilder field) {
        if (fields.size() >= MAX_FIELDS) {
            throw new IllegalArgumentException("too many columns");
        }
        fields.add(field.toString());
    }

    private static void append(StringBuilder field, char ch) {
        if (field.length() >= MAX_FIELD_LENGTH) {
            throw new IllegalArgumentException("field longer than " + MAX_FIELD_LENGTH + " characters");
        }
        field.append(ch);
    }

    private int read() {
        try {
            int c;
            if (pushback != NONE) {
                c = pushback;
                pushback = NONE;
            } else {
                c = reader.read();
            }
            if (first) {
                first = false;
                if (c == 0xFEFF) {
                    return read();
                }
            }
            return c;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
