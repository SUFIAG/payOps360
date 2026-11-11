package com.payops.reconciliation.domain;

public enum ResolutionReason {
    PROVIDER_CORRECTED,
    LEDGER_CORRECTED,
    TIMING_DIFFERENCE,
    REFUND_RECOVERED,
    FALSE_POSITIVE,
    MANUAL_MATCH,
    SETTLED_LATER,
    OTHER
}
