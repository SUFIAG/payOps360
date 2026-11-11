package com.payops.provider.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/**
 * Health verdict for one provider over one window, judged against its SLA.
 */
public record HealthSnapshot(long providerId, Instant windowStart, Instant windowEnd, int total, int succeeded,
                             int failed, BigDecimal successRate, int avgLatencyMs, int p95LatencyMs,
                             HealthStatus status) {

    /** Below this many completed payments the window is statistically meaningless. */
    static final int MIN_SAMPLE = 10;
    private static final BigDecimal CRITICAL_GAP = BigDecimal.valueOf(10);

    public static HealthSnapshot evaluate(Provider provider, TrafficWindow traffic) {
        int completed = traffic.succeeded() + traffic.failed();
        BigDecimal successRate = completed == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(traffic.succeeded() * 100.0 / completed).setScale(2, RoundingMode.HALF_UP);
        HealthStatus status;
        if (completed < MIN_SAMPLE) {
            status = HealthStatus.UNKNOWN;
        } else if (successRate.compareTo(provider.slaSuccessRate().subtract(CRITICAL_GAP)) < 0
                || traffic.p95LatencyMs() > provider.slaLatencyMs() * 2) {
            status = HealthStatus.CRITICAL;
        } else if (successRate.compareTo(provider.slaSuccessRate()) < 0
                || traffic.p95LatencyMs() > provider.slaLatencyMs()) {
            status = HealthStatus.DEGRADED;
        } else {
            status = HealthStatus.HEALTHY;
        }
        return new HealthSnapshot(provider.id(), traffic.windowStart(), traffic.windowEnd(), traffic.total(),
                traffic.succeeded(), traffic.failed(), successRate, traffic.avgLatencyMs(), traffic.p95LatencyMs(),
                status);
    }
}
