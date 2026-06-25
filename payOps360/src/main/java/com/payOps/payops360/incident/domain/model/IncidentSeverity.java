package com.payOps.payops360.incident.domain.model;

/**
 * Incident Severity Levels
 */
public enum IncidentSeverity {
    LOW,        // Minor impact, can wait
    MEDIUM,     // Moderate impact, needs attention
    HIGH,       // Significant impact, urgent
    CRITICAL    // System-wide impact, immediate action required
}

