package com.payops.payops360.provider.adapter.in.rest.dto;

import com.payops.payops360.provider.domain.model.HealthStatus;
import com.payops.payops360.provider.domain.model.UptimeStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * DTO for provider health response.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderHealthResponse {

    private Long id;
    private String providerId;

    private Instant windowStart;
    private Instant windowEnd;
    private Instant snapshotAt;

    private long successCount;
    private long failureCount;
    private long timeoutCount;
    private long totalCount;

    private double successRate;
    private double failureRate;
    private double timeoutRate;

    private long avgLatencyMs;
    private long p50LatencyMs;
    private long p95LatencyMs;
    private long p99LatencyMs;

    private Map<String, Long> errorDistribution;

    private HealthStatus healthStatus;
    private UptimeStatus uptimeStatus;
    private double slaCompliance;

    private int healthScore;
    private String recommendedAction;
}

