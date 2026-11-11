package com.payops.reconciliation.domain;

import com.payops.shared.domain.DomainException;

import java.io.Reader;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Turns a settlement CSV stream into {@link SettlementRecord}s one row at a
 * time using a {@link MappingProfile}. Invalid rows are reported, not thrown,
 * so one bad line does not reject the whole file; a malformed header or CSV
 * structure does.
 */
public final class SettlementParser {

    public sealed interface Row permits Parsed, Rejected {
    }

    public record Parsed(SettlementRecord record) implements Row {
    }

    public record Rejected(int lineNo, String reason) implements Row {
    }

    private static final Pattern CURRENCY = Pattern.compile("^[A-Z]{3}$");
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("1000000000000");

    private final SettlementCsvReader csv;
    private final MappingProfile profile;
    private final DateTimeFormatter customFormat;
    private final Map<MappingProfile.Field, Integer> index = new EnumMap<>(MappingProfile.Field.class);

    public SettlementParser(MappingProfile profile, Reader reader) {
        this.profile = profile;
        this.csv = new SettlementCsvReader(reader, profile.delimiter());
        this.customFormat = profile.dateFormat() == null ? null : formatter(profile.dateFormat());
        readHeader();
    }

    private static DateTimeFormatter formatter(String pattern) {
        try {
            return DateTimeFormatter.ofPattern(pattern, Locale.ROOT);
        } catch (IllegalArgumentException e) {
            throw new DomainException("INVALID_MAPPING", "Invalid date format pattern");
        }
    }

    private void readHeader() {
        List<String> header;
        try {
            header = csv.next();
        } catch (IllegalArgumentException e) {
            throw new DomainException("INVALID_SETTLEMENT_FILE", "Header row is malformed: " + e.getMessage());
        }
        if (header == null) {
            throw new DomainException("INVALID_SETTLEMENT_FILE", "The file is empty");
        }
        for (Map.Entry<MappingProfile.Field, String> column : profile.columns().entrySet()) {
            for (int i = 0; i < header.size(); i++) {
                if (header.get(i).trim().equalsIgnoreCase(column.getValue())) {
                    index.put(column.getKey(), i);
                    break;
                }
            }
            if (column.getKey().required() && !index.containsKey(column.getKey())) {
                throw new DomainException("INVALID_SETTLEMENT_FILE",
                        "Missing column '" + column.getValue() + "' (" + column.getKey() + ") in the header row");
            }
        }
    }

    /** Next row, or {@code null} at end of file. */
    public Row next() {
        List<String> fields;
        try {
            fields = csv.next();
        } catch (IllegalArgumentException e) {
            throw new DomainException("INVALID_SETTLEMENT_FILE",
                    "Malformed CSV near line " + (csv.recordNumber() + 1) + ": " + e.getMessage());
        }
        if (fields == null) {
            return null;
        }
        int lineNo = (int) csv.recordNumber();
        try {
            SettlementLineType type = SettlementLineType.parse(value(fields, MappingProfile.Field.TYPE));
            String reference = value(fields, MappingProfile.Field.REFERENCE);
            if (reference == null && type != SettlementLineType.FEE) {
                return new Rejected(lineNo, "reference is required");
            }
            if (reference != null && reference.length() > 100) {
                return new Rejected(lineNo, "reference longer than 100 characters");
            }
            BigDecimal amount = decimal(value(fields, MappingProfile.Field.AMOUNT), "amount");
            if (amount == null) {
                return new Rejected(lineNo, "amount is required");
            }
            String currency = value(fields, MappingProfile.Field.CURRENCY);
            currency = currency == null ? null : currency.toUpperCase(Locale.ROOT);
            if (currency == null || !CURRENCY.matcher(currency).matches()) {
                return new Rejected(lineNo, "currency must be a 3-letter ISO code");
            }
            BigDecimal fee = decimal(value(fields, MappingProfile.Field.FEE), "fee");
            BigDecimal fxRate = decimal(value(fields, MappingProfile.Field.FX_RATE), "fx rate");
            if (fxRate != null && fxRate.signum() <= 0) {
                return new Rejected(lineNo, "fx rate must be positive");
            }
            Instant settledAt = instant(value(fields, MappingProfile.Field.SETTLED_AT));
            return new Parsed(new SettlementRecord(lineNo, type, reference, amount, currency,
                    fee == null ? null : fee.abs(), fxRate, settledAt));
        } catch (IllegalArgumentException | DateTimeParseException e) {
            return new Rejected(lineNo, e.getMessage());
        }
    }

    private String value(List<String> fields, MappingProfile.Field field) {
        Integer i = index.get(field);
        if (i == null || i >= fields.size()) {
            return null;
        }
        String v = fields.get(i).trim();
        return v.isEmpty() ? null : v;
    }

    private static BigDecimal decimal(String raw, String name) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw.replace(" ", "").replace("_", "");
        if (cleaned.contains(",") && cleaned.contains(".")) {
            cleaned = cleaned.replace(",", "");
        } else if (cleaned.contains(",")) {
            cleaned = cleaned.replace(',', '.');
        }
        BigDecimal value;
        try {
            value = new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + " '" + truncate(raw) + "' is not a number");
        }
        if (value.abs().compareTo(MAX_AMOUNT) >= 0) {
            throw new IllegalArgumentException(name + " is out of range");
        }
        return value;
    }

    private Instant instant(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("settled at is required");
        }
        if (customFormat != null) {
            try {
                return LocalDateTime.parse(raw, customFormat).toInstant(ZoneOffset.UTC);
            } catch (DateTimeParseException e) {
                try {
                    return LocalDate.parse(raw, customFormat).atStartOfDay().toInstant(ZoneOffset.UTC);
                } catch (DateTimeParseException e2) {
                    throw new IllegalArgumentException("settled at '" + truncate(raw) + "' does not match the date format");
                }
            }
        }
        try {
            return OffsetDateTime.parse(raw).toInstant();
        } catch (DateTimeParseException ignored) {
            // try the next format
        }
        try {
            return LocalDateTime.parse(raw.replace(' ', 'T')).toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
            // try the next format
        }
        try {
            return LocalDate.parse(raw).atStartOfDay().toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("settled at '" + truncate(raw) + "' is not a recognised date");
        }
    }

    private static String truncate(String raw) {
        return raw.length() <= 40 ? raw : raw.substring(0, 40) + "...";
    }
}
