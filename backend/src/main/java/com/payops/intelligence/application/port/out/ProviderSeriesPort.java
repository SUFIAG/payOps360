package com.payops.intelligence.application.port.out;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Per-provider metric series used by the anomaly scanner.
 */
public interface ProviderSeriesPort {

    List<ProviderSeries> series(Instant from, Instant to, Duration bucket);

    record Bucket(Instant start, long total, long failed, int p95LatencyMs) {

        public double failureRate() {
            return total == 0 ? 0 : failed * 100.0 / total;
        }
    }

    record ProviderSeries(long organizationId, long providerId, String providerCode, String providerName,
                          List<Bucket> buckets) {
    }
}
