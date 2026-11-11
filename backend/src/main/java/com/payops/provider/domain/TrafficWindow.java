package com.payops.provider.domain;

import java.time.Instant;

/**
 * Raw traffic observed for one provider in a time window.
 */
public record TrafficWindow(long providerId, Instant windowStart, Instant windowEnd, int total, int succeeded,
                            int failed, int avgLatencyMs, int p95LatencyMs) {
}
