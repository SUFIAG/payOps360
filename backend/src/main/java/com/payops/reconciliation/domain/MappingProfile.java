package com.payops.reconciliation.domain;

import com.payops.shared.domain.DomainException;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * How one provider's settlement CSV maps onto settlement fields. Header names
 * are matched case-insensitively. {@code dateFormat} is an optional
 * {@link java.time.format.DateTimeFormatter} pattern; without it ISO-8601
 * instants, local date-times (UTC) and dates are accepted.
 */
public record MappingProfile(char delimiter, String dateFormat, Map<Field, String> columns) {

    public enum Field {
        REFERENCE(true),
        AMOUNT(true),
        CURRENCY(true),
        FEE(false),
        FX_RATE(false),
        SETTLED_AT(true),
        TYPE(false);

        private final boolean required;

        Field(boolean required) {
            this.required = required;
        }

        public boolean required() {
            return required;
        }
    }

    private static final Set<Character> DELIMITERS = Set.of(',', ';', '|', '\t');

    public MappingProfile {
        if (!DELIMITERS.contains(delimiter)) {
            throw new DomainException("INVALID_MAPPING", "Delimiter must be one of , ; | or tab");
        }
        Map<Field, String> copy = new EnumMap<>(Field.class);
        if (columns != null) {
            columns.forEach((field, header) -> {
                if (field != null && header != null && !header.isBlank()) {
                    copy.put(field, header.trim());
                }
            });
        }
        for (Field field : Field.values()) {
            if (field.required() && !copy.containsKey(field)) {
                throw new DomainException("INVALID_MAPPING", "A column for " + field + " is required");
            }
        }
        if (copy.values().stream().map(String::toLowerCase).distinct().count() != copy.size()) {
            throw new DomainException("INVALID_MAPPING", "Each field must map to a different column");
        }
        dateFormat = dateFormat == null || dateFormat.isBlank() ? null : dateFormat.trim();
        columns = Map.copyOf(copy);
    }

    public static MappingProfile defaults() {
        Map<Field, String> columns = new EnumMap<>(Field.class);
        columns.put(Field.REFERENCE, "provider_reference");
        columns.put(Field.AMOUNT, "amount");
        columns.put(Field.CURRENCY, "currency");
        columns.put(Field.FEE, "fee");
        columns.put(Field.FX_RATE, "fx_rate");
        columns.put(Field.SETTLED_AT, "settled_at");
        columns.put(Field.TYPE, "type");
        return new MappingProfile(',', null, columns);
    }

    /** Mapped fields in canonical order, used when generating sample files. */
    public List<Field> orderedFields() {
        return Arrays.stream(Field.values()).filter(columns::containsKey).toList();
    }
}
