package com.payops.payops360.provider.domain.model;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.Map;

/**
 * Provider Health Snapshot - Domain Model
 *
 * Represents health metrics for a provider at a specific point in time.
 * Pure domain model with no framework dependencies.
 */
@Getter
@Builder(toBuilder = true)
public class ProviderHealth {

    // Identity
    private final Long id;
    private final String providerId;

    // Time Window
    private final Instant windowStart;
    private final Instant windowEnd;
    private final Instant snapshotAt;

    // Volume Metrics
    private final long successCount;
    private final long failureCount;
    private final long timeoutCount;
    private final long totalCount;

    // Calculated Rates (%)
    private final double successRate;
    private final double failureRate;
    private final double timeoutRate;

    // Latency Metrics (milliseconds)
    private final long avgLatencyMs;
    private final long p50LatencyMs;
    private final long p95LatencyMs;
    private final long p99LatencyMs;

    // Error Distribution
    @Builder.Default
    private final Map<String, Long> errorDistribution = Map.of();

    // Health Assessment
    private final HealthStatus healthStatus;
    private final UptimeStatus uptimeStatus;
    private final double slaCompliance;  // % of SLA met

    /**
     * Check if health is acceptable
     */
    public boolean isHealthy() {
        return healthStatus == HealthStatus.HEALTHY;
    }

    /**
     * Check if health is degraded
     */
    public boolean isDegraded() {
        return healthStatus == HealthStatus.DEGRADED;
    }

    /**
     * Check if health is critical
     */
    public boolean isCritical() {
        return healthStatus == HealthStatus.CRITICAL;
    }

    /**
     * Check if provider is up
     */
    public boolean isUp() {
        return uptimeStatus == UptimeStatus.UP;
    }

    /**
     * Check if provider is down
     */
    public boolean isDown() {
        return uptimeStatus == UptimeStatus.DOWN;
    }

    /**
     * Check if latency is within acceptable range
     */
    public boolean hasAcceptableLatency(long slaLatencyMs) {
        return p95LatencyMs <= slaLatencyMs;
    }

    /**
     * Check if success rate is acceptable
     */
    public boolean hasAcceptableSuccessRate(double slaSuccessRate) {
        return successRate >= slaSuccessRate;
    }

    /**
     * Get health score (0-100)
     */
    public int getHealthScore() {
        int score = 100;

        // Deduct for low success rate
        if (successRate < 98.0) score -= 30;
        else if (successRate < 99.0) score -= 15;

        // Deduct for high timeout rate
        if (timeoutRate > 5.0) score -= 25;
        else if (timeoutRate > 2.0) score -= 10;

        // Deduct for poor uptime
        if (uptimeStatus == UptimeStatus.DOWN) score -= 50;
        else if (uptimeStatus == UptimeStatus.DEGRADED) score -= 20;

        return Math.max(0, score);
    }

    /**
     * Get recommended action based on health
     */
    public String getRecommendedAction() {
        if (isCritical()) {
            return "IMMEDIATE_ATTENTION - Provider critically degraded";
        }
        if (isDegraded()) {
            return "MONITOR_CLOSELY - Provider showing degradation";
        }
        if (timeoutRate > 5.0) {
            return "INVESTIGATE_TIMEOUTS - High timeout rate detected";
        }
        if (successRate < 98.0) {
            return "INVESTIGATE_FAILURES - Success rate below threshold";
        }
        return "NONE - Provider operating normally";
    }
}

