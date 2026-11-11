package com.payops.reconciliation.domain;

/**
 * Exception workflow: OPEN -> INVESTIGATING -> RESOLVED | WRITTEN_OFF.
 * RESOLVED and WRITTEN_OFF are terminal.
 */
public enum ExceptionStatus {
    OPEN,
    INVESTIGATING,
    RESOLVED,
    WRITTEN_OFF;

    public boolean isTerminal() {
        return this == RESOLVED || this == WRITTEN_OFF;
    }
}
