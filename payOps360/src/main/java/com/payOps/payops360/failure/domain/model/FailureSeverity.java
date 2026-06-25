package com.payOps.payops360.failure.domain.model;

/**
 * Failure Severity Levels
 */
public enum FailureSeverity {
    CRITICAL,   // System-wide impact, immediate action required
    HIGH,       // Significant impact, urgent attention needed
    MEDIUM,     // Moderate impact, should be addressed soon
    LOW         // Minor impact, can wait
}

