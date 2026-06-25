package com.payOps.payops360.incident.domain.model;

/**
 * Incident Categories based on root cause type
 */
public enum IncidentCategory {
    PROVIDER_OUTAGE,        // Payment provider is down
    NETWORK_ISSUE,          // Network connectivity problems
    PERFORMANCE_DEGRADATION,// Slow response times
    HIGH_FAILURE_RATE,      // Spike in failure rate
    FRAUD_SPIKE,            // Unusual fraud activity
    SYSTEM_ERROR,           // Internal system errors
    UNKNOWN                 // Unknown category
}

