package com.payops.payops360.provider.domain.service;

import com.payops.payops360.provider.domain.model.HealthStatus;
import com.payops.payops360.provider.domain.model.ProviderHealth;
import com.payops.payops360.provider.domain.model.UptimeStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Provider Health Calculation Service - Domain Service
 *
 * Pure business logic for calculating provider health metrics.
 * No framework dependencies.
 */
public class ProviderHealthCalculator {

    /**
     * Calculate health metrics from raw data
     */
    public ProviderHealth calculateHealth(
            String providerId,
            Instant windowStart,
            Instant windowEnd,
            long successCount,
            long failureCount,
            long timeoutCount,
            List<Long> latencies,
            Map<String, Long> errorDistribution,
            long slaLatencyMs,
            double slaSuccessRate) {

        // Calculate totals
        long totalCount = successCount + failureCount + timeoutCount;

        // Calculate rates
        double successRate = totalCount > 0 ? (successCount * 100.0 / totalCount) : 0.0;
        double failureRate = totalCount > 0 ? (failureCount * 100.0 / totalCount) : 0.0;
        double timeoutRate = totalCount > 0 ? (timeoutCount * 100.0 / totalCount) : 0.0;

        // Calculate latency percentiles
        LatencyMetrics latencyMetrics = calculateLatencyMetrics(latencies);

        // Determine health status
        HealthStatus healthStatus = HealthStatus.fromMetrics(
                successRate,
                latencyMetrics.p95,
                slaLatencyMs
        );

        // Determine uptime status
        UptimeStatus uptimeStatus = determineUptimeStatus(successRate, timeoutRate);

        // Calculate SLA compliance
        double slaCompliance = calculateSLACompliance(
                successRate,
                slaSuccessRate,
                latencyMetrics.p95,
                slaLatencyMs
        );

        return ProviderHealth.builder()
                .providerId(providerId)
                .windowStart(windowStart)
                .windowEnd(windowEnd)
                .snapshotAt(Instant.now())
                .successCount(successCount)
                .failureCount(failureCount)
                .timeoutCount(timeoutCount)
                .totalCount(totalCount)
                .successRate(round(successRate, 2))
                .failureRate(round(failureRate, 2))
                .timeoutRate(round(timeoutRate, 2))
                .avgLatencyMs(latencyMetrics.avg)
                .p50LatencyMs(latencyMetrics.p50)
                .p95LatencyMs(latencyMetrics.p95)
                .p99LatencyMs(latencyMetrics.p99)
                .errorDistribution(errorDistribution)
                .healthStatus(healthStatus)
                .uptimeStatus(uptimeStatus)
                .slaCompliance(round(slaCompliance, 2))
                .build();
    }

    /**
     * Calculate latency percentiles
     */
    private LatencyMetrics calculateLatencyMetrics(List<Long> latencies) {
        if (latencies == null || latencies.isEmpty()) {
            return new LatencyMetrics(0, 0, 0, 0);
        }

        List<Long> sorted = latencies.stream().sorted().toList();
        int size = sorted.size();

        long avg = (long) sorted.stream().mapToLong(Long::longValue).average().orElse(0.0);
        long p50 = sorted.get((int) (size * 0.50));
        long p95 = sorted.get((int) (size * 0.95));
        long p99 = sorted.get(Math.min((int) (size * 0.99), size - 1));

        return new LatencyMetrics(avg, p50, p95, p99);
    }

    /**
     * Determine uptime status based on metrics
     */
    private UptimeStatus determineUptimeStatus(double successRate, double timeoutRate) {
        if (successRate < 80.0 || timeoutRate > 20.0) {
            return UptimeStatus.DOWN;
        }
        if (successRate < 95.0 || timeoutRate > 5.0) {
            return UptimeStatus.DEGRADED;
        }
        return UptimeStatus.UP;
    }

    /**
     * Calculate SLA compliance percentage
     */
    private double calculateSLACompliance(
            double actualSuccessRate,
            double slaSuccessRate,
            long actualLatency,
            long slaLatency) {

        double successRateCompliance = actualSuccessRate >= slaSuccessRate ? 100.0 :
                (actualSuccessRate / slaSuccessRate * 100.0);

        double latencyCompliance = actualLatency <= slaLatency ? 100.0 :
                (slaLatency / (double) actualLatency * 100.0);

        // Average of both metrics
        return (successRateCompliance + latencyCompliance) / 2.0;
    }

    /**
     * Round to specified decimal places
     */
    private double round(double value, int places) {
        double scale = Math.pow(10, places);
        return Math.round(value * scale) / scale;
    }

    /**
     * Helper class for latency metrics
     */
    private record LatencyMetrics(long avg, long p50, long p95, long p99) {}
}

