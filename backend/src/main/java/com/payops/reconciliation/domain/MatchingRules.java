package com.payops.reconciliation.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Pure comparison rules used by the matching engine.
 */
public final class MatchingRules {

    private MatchingRules() {
    }

    /**
     * Settlement amount the ledger expects for a line: the payment amount,
     * converted with the line's FX rate when the provider settled in another
     * currency. {@code null} when the currencies differ and no rate is given.
     */
    public static BigDecimal expectedAmount(BigDecimal paymentAmount, String paymentCurrency, String lineCurrency,
                                            BigDecimal fxRate) {
        if (paymentCurrency.equals(lineCurrency)) {
            return paymentAmount;
        }
        return fxRate == null ? null : paymentAmount.multiply(fxRate).setScale(4, RoundingMode.HALF_UP);
    }

    /** True when the magnitudes differ by no more than {@code tolerance}. */
    public static boolean withinTolerance(BigDecimal expected, BigDecimal actual, BigDecimal tolerance) {
        return expected != null && expected.abs().subtract(actual.abs()).abs().compareTo(tolerance) <= 0;
    }

    /** Refunds and chargebacks may be partial: they match when they do not exceed the payment. */
    public static boolean partialWithinTolerance(BigDecimal expected, BigDecimal actual, BigDecimal tolerance) {
        return expected != null && actual.abs().compareTo(expected.abs().add(tolerance)) <= 0;
    }

    /** Amount charged above the expected fee when the excess exceeds {@code tolerance}, else zero. */
    public static BigDecimal overcharge(BigDecimal expectedFee, BigDecimal actualFee, BigDecimal tolerance) {
        if (expectedFee == null || actualFee == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal excess = actualFee.abs().subtract(expectedFee);
        return excess.compareTo(tolerance) > 0 ? excess.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
    }
}
