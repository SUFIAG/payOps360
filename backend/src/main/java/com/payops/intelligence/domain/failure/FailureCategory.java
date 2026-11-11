package com.payops.intelligence.domain.failure;

public enum FailureCategory {
    /** Customer-side, may succeed later (e.g. insufficient funds). */
    SOFT_DECLINE,
    /** Will not succeed with the same instrument. */
    HARD_DECLINE,
    /** Provider / network problem; retry or reroute. */
    TECHNICAL,
    /** Risk or fraud controls blocked the payment. */
    RISK,
    /** Regulatory or compliance hold. */
    COMPLIANCE,
    /** Bad request data or configuration. */
    VALIDATION,
    UNKNOWN
}
