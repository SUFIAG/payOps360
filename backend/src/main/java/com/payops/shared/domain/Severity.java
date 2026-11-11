package com.payops.shared.domain;

public enum Severity {
    LOW, MEDIUM, HIGH, CRITICAL;

    public boolean isAtLeast(Severity other) {
        return this.ordinal() >= other.ordinal();
    }
}
