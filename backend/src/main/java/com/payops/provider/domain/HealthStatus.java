package com.payops.provider.domain;

public enum HealthStatus {
    HEALTHY,
    DEGRADED,
    CRITICAL,
    /** Not enough traffic in the window to judge. */
    UNKNOWN
}
