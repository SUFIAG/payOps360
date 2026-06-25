package com.payOps.payops360.incident.domain.model;

/**
 * Incident Status
 */
public enum IncidentStatus {
    OPEN,           // Newly detected, not yet acknowledged
    INVESTIGATING,  // Being investigated by team
    RESOLVED        // Root cause found and resolved
}

