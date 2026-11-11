package com.payops.payment.domain;

import com.payops.shared.domain.DomainException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;

/**
 * Monetary amount in an ISO-4217 currency, scaled to the currency's minor units.
 */
public record Money(BigDecimal amount, String currency) {

    private static final BigDecimal MAX_AMOUNT = new BigDecimal("1000000000");

    public Money {
        if (amount == null || currency == null) {
            throw new DomainException("INVALID_MONEY", "Amount and currency are required");
        }
        Currency iso;
        try {
            iso = Currency.getInstance(currency.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new DomainException("INVALID_CURRENCY", "Unknown ISO-4217 currency: " + currency);
        }
        if (amount.signum() <= 0 || amount.compareTo(MAX_AMOUNT) > 0) {
            throw new DomainException("INVALID_AMOUNT", "Amount must be positive and below 1,000,000,000");
        }
        int scale = Math.max(iso.getDefaultFractionDigits(), 0);
        if (amount.stripTrailingZeros().scale() > scale) {
            throw new DomainException("INVALID_AMOUNT", "Amount has more decimals than " + iso.getCurrencyCode() + " allows");
        }
        amount = amount.setScale(scale, RoundingMode.UNNECESSARY);
        currency = iso.getCurrencyCode();
    }

    public static Money of(String amount, String currency) {
        return new Money(new BigDecimal(amount), currency);
    }
}
