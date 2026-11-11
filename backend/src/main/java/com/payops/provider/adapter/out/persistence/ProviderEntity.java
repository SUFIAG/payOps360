package com.payops.provider.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "providers")
class ProviderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "organization_id", nullable = false, updatable = false)
    private Long organizationId;
    @Column(nullable = false, updatable = false)
    private String code;
    private String name;
    private String type;
    private String region;
    @Column(name = "sla_latency_ms")
    private int slaLatencyMs;
    @Column(name = "sla_success_rate")
    private BigDecimal slaSuccessRate;
    @Column(name = "cost_bps")
    private BigDecimal costBps;
    private boolean active;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
