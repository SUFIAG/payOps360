package com.payops.intelligence.domain.risk;

import java.math.BigDecimal;

/**
 * Behavioural features of a payment and its customer, computed at ingestion.
 */
public record RiskSignals(
        BigDecimal amountUsd,
        BigDecimal customerAverageUsd,
        long customerPaymentsLast30Days,
        long customerPaymentsLastHour,
        long customerFailuresLast24h,
        long customerCountriesLast24h,
        String method,
        String country,
        int hourOfDayUtc) {

    public boolean isNewCustomer() {
        return customerPaymentsLast30Days == 0;
    }
}
