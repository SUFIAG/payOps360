package com.payops.reconciliation.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One parsed settlement line. {@code amount} keeps the provider's sign
 * (refunds and chargebacks are often negative); matching compares magnitudes.
 */
public record SettlementRecord(int lineNo, SettlementLineType type, String reference, BigDecimal amount,
                               String currency, BigDecimal fee, BigDecimal fxRate, Instant settledAt) {
}
