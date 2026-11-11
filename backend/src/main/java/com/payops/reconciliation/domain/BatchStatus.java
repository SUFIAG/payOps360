package com.payops.reconciliation.domain;

/**
 * Settlement batch lifecycle: RECEIVED (lines stored) -> MATCHING -> COMPLETED | FAILED.
 */
public enum BatchStatus {
    RECEIVED,
    MATCHING,
    COMPLETED,
    FAILED
}
