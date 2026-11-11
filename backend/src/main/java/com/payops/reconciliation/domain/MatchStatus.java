package com.payops.reconciliation.domain;

/**
 * Outcome of matching one settlement line against the payment ledger.
 */
public enum MatchStatus {
    PENDING,
    MATCHED,
    AMOUNT_MISMATCH,
    MISSING_IN_LEDGER,
    DUPLICATE,
    /** Provider-level fee line with no payment counterpart. */
    FEE_ONLY
}
