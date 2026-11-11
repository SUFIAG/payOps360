package com.payops.analytics.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Read models for operational analytics. Monetary values are expressed in
 * USD (indicative conversion, see {@link FxRates}).
 */
public final class AnalyticsModels {

    private AnalyticsModels() {
    }

    public record WindowStats(long total, long succeeded, long failed, long inFlight, long stuck, long highRisk,
                              BigDecimal volumeUsd, BigDecimal failedVolumeUsd, int avgLatencyMs, int p95LatencyMs,
                              long uniqueCustomers) {

        public double successRate() {
            long completed = succeeded + failed;
            return completed == 0 ? 0 : succeeded * 100.0 / completed;
        }
    }

    public record Kpi(String key, String label, double value, Double previous, String unit) {

        public Double changePercent() {
            if (previous == null || previous == 0) {
                return null;
            }
            return (value - previous) * 100.0 / Math.abs(previous);
        }
    }

    public record Overview(Instant from, Instant to, List<Kpi> kpis, WindowStats current, WindowStats previous) {
    }

    public record TimeBucket(Instant bucket, long total, long succeeded, long failed, BigDecimal volumeUsd,
                             int avgLatencyMs) {
    }

    public record ProviderPerformance(long providerId, String code, String name, long total, long succeeded,
                                      long failed, double successRate, int avgLatencyMs, int p95LatencyMs,
                                      BigDecimal volumeUsd, BigDecimal slaSuccessRate, int slaLatencyMs,
                                      BigDecimal costBps) {
    }

    public record Breakdown(String label, long count, BigDecimal volumeUsd) {
    }

    public record Corridor(String country, long count, BigDecimal volumeUsd, double successRate) {
    }
}
