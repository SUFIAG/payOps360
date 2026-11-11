package com.payops.reconciliation.domain;

public enum ExceptionType {
    AMOUNT_MISMATCH,
    /** Payment succeeded but no settlement reported it in time. */
    MISSING_IN_SETTLEMENT,
    /** Settlement line with no payment in the ledger. */
    MISSING_IN_LEDGER,
    DUPLICATE,
    FEE_OVERCHARGE;

    /** Exceptions that point at a settlement line and can be manually matched to a payment. */
    public boolean hasSettlementLine() {
        return this != MISSING_IN_SETTLEMENT;
    }
}
