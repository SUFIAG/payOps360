package com.payops.payops360.provider.adapter.out.persistence.entity;

import com.payops.payops360.provider.domain.model.HealthStatus;
import com.payops.payops360.provider.domain.model.UptimeStatus;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.Map;

/**
 * Provider Health JPA Entity - OUTPUT ADAPTER
 */
@Entity
@Table(name = "provider_health_snapshots", indexes = {
        @Index(name = "idx_health_provider", columnList = "provider_id"),
        @Index(name = "idx_health_snapshot_at", columnList = "snapshot_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProviderHealthEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "provider_id", nullable = false)
    private String providerId;

    @Column(name = "window_start", nullable = false)
    private Instant windowStart;

    @Column(name = "window_end", nullable = false)
    private Instant windowEnd;

    @Column(name = "snapshot_at", nullable = false)
    private Instant snapshotAt;

    @Column(name = "success_count")
    private long successCount;

    @Column(name = "failure_count")
    private long failureCount;

    @Column(name = "timeout_count")
    private long timeoutCount;

    @Column(name = "total_count")
    private long totalCount;

    @Column(name = "success_rate")
    private double successRate;

    @Column(name = "failure_rate")
    private double failureRate;

    @Column(name = "timeout_rate")
    private double timeoutRate;

    @Column(name = "avg_latency_ms")
    private long avgLatencyMs;

    @Column(name = "p50_latency_ms")
    private long p50LatencyMs;

    @Column(name = "p95_latency_ms")
    private long p95LatencyMs;

    @Column(name = "p99_latency_ms")
    private long p99LatencyMs;

    @Type(JsonBinaryType.class)
    @Column(name = "error_distribution", columnDefinition = "jsonb")
    private Map<String, Long> errorDistribution;

    @Enumerated(EnumType.STRING)
    @Column(name = "health_status")
    private HealthStatus healthStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "uptime_status")
    private UptimeStatus uptimeStatus;

    @Column(name = "sla_compliance")
    private double slaCompliance;
}

