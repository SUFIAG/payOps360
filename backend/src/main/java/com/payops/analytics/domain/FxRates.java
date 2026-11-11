package com.payops.analytics.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * Indicative FX rates (units of currency per 1 USD) used only to aggregate
 * multi-currency volume on dashboards. Never used for settlement. Replace
 * with a market-data provider adapter in production.
 */
public final class FxRates {

    private static final Map<String, BigDecimal> PER_USD = Map.ofEntries(
            Map.entry("USD", new BigDecimal("1")),
            Map.entry("EUR", new BigDecimal("0.92")),
            Map.entry("GBP", new BigDecimal("0.79")),
            Map.entry("SAR", new BigDecimal("3.75")),
            Map.entry("AED", new BigDecimal("3.6725")),
            Map.entry("PKR", new BigDecimal("280")),
            Map.entry("INR", new BigDecimal("83.5")),
            Map.entry("PHP", new BigDecimal("57")),
            Map.entry("BDT", new BigDecimal("118")),
            Map.entry("EGP", new BigDecimal("48")),
            Map.entry("NGN", new BigDecimal("1500")),
            Map.entry("MXN", new BigDecimal("17.5")),
            Map.entry("KES", new BigDecimal("129")));

    private FxRates() {
    }

    public static BigDecimal toUsd(BigDecimal amount, String currency) {
        BigDecimal rate = PER_USD.get(currency);
        if (rate == null || amount == null) {
            return BigDecimal.ZERO;
        }
        return amount.divide(rate, 2, RoundingMode.HALF_UP);
    }

    public static BigDecimal fromUsd(BigDecimal usd, String currency) {
        BigDecimal rate = PER_USD.getOrDefault(currency, BigDecimal.ONE);
        return usd.multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }

    /** SQL CASE expression converting {@code amount} in {@code currency} to USD. */
    public static String sqlToUsd(String amountColumn, String currencyColumn) {
        StringBuilder sb = new StringBuilder("(").append(amountColumn).append(" / CASE ").append(currencyColumn);
        PER_USD.forEach((code, rate) -> sb.append(" WHEN '").append(code).append("' THEN ").append(rate.toPlainString()));
        return sb.append(" ELSE 1 END)").toString();
    }
}
