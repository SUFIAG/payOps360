package com.payops.reconciliation.domain;

import java.util.Locale;

/**
 * Kind of movement reported on a provider settlement file.
 */
public enum SettlementLineType {
    SALE,
    REFUND,
    CHARGEBACK,
    FEE;

    /** Lenient parse of common provider vocabularies; blank means SALE. */
    public static SettlementLineType parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return SALE;
        }
        return switch (raw.trim().toUpperCase(Locale.ROOT)) {
            case "SALE", "CAPTURE", "PAYMENT", "SETTLEMENT", "SETTLED" -> SALE;
            case "REFUND", "REFUNDED" -> REFUND;
            case "CHARGEBACK", "DISPUTE", "CHARGEBACK_DEBIT" -> CHARGEBACK;
            case "FEE", "FEES", "SCHEME_FEE", "MONTHLY_FEE" -> FEE;
            default -> throw new IllegalArgumentException("unknown type '" + raw.trim() + "'");
        };
    }
}
