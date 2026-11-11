package com.payops.reconciliation.domain;

public enum MatchMethod {
    /** Line reference equals a payment, merchant or provider reference. */
    EXACT,
    /** Amount + currency + date window on the same provider. */
    FUZZY,
    /** Linked by an operator from the exception queue. */
    MANUAL
}
