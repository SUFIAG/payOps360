package com.payops.payops360.provider.domain.model;

/**
 * Provider health status based on metrics.
 */
public enum HealthStatus {

    HEALTHY("All metrics within acceptable range"),
    DEGRADED("Some metrics below threshold"),
    CRITICAL("Critical metrics failing");

    private final String description;

    HealthStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Determine health status from success rate and latency
     */
    public static HealthStatus fromMetrics(double successRate, long p95LatencyMs, long slaLatencyMs) {
        // Critical conditions
        if (successRate < 95.0) {
            return CRITICAL;
        }
        if (p95LatencyMs > slaLatencyMs * 2) {
            return CRITICAL;
        }

        // Degraded conditions
        if (successRate < 98.0) {
            return DEGRADED;
        }
        if (p95LatencyMs > slaLatencyMs) {
            return DEGRADED;
        }

        // Healthy
        return HEALTHY;
    }
}

