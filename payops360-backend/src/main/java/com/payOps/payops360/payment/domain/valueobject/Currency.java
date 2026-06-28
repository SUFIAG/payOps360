package com.payops.payops360.payment.domain.valueobject;

import lombok.Getter;

/**
 * Supported currencies with their properties.
 * In production, this could be loaded from a configuration or database.
 */
@Getter
public enum Currency {

    USD("US Dollar", "$", 2),
    EUR("Euro", "€", 2),
    GBP("British Pound", "£", 2),
    JPY("Japanese Yen", "¥", 0),
    INR("Indian Rupee", "₹", 2),
    AUD("Australian Dollar", "A$", 2),
    CAD("Canadian Dollar", "C$", 2),
    CHF("Swiss Franc", "CHF", 2),
    CNY("Chinese Yuan", "¥", 2),
    SEK("Swedish Krona", "kr", 2);

    private final String displayName;
    private final String symbol;
    private final int decimalPlaces;

    Currency(String displayName, String symbol, int decimalPlaces) {
        this.displayName = displayName;
        this.symbol = symbol;
        this.decimalPlaces = decimalPlaces;
    }

    /**
     * Get currency from ISO code (case-insensitive)
     */
    public static Currency fromCode(String code) {
        try {
            return Currency.valueOf(code.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Currency not supported: " + code);
        }
    }
}

