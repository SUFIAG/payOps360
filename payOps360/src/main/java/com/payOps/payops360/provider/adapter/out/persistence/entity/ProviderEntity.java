package com.payops.payops360.provider.adapter.out.persistence.entity;

import com.payops.payops360.common.util.BaseEntity;
import com.payops.payops360.provider.domain.model.ProviderType;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.util.Map;

/**
 * Provider JPA Entity - OUTPUT ADAPTER
 */
@Entity
@Table(name = "providers", indexes = {
        @Index(name = "idx_provider_provider_id", columnList = "provider_id"),
        @Index(name = "idx_provider_active", columnList = "is_active")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProviderEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "provider_id", unique = true, nullable = false)
    private String providerId;

    @Column(name = "provider_name", nullable = false)
    private String providerName;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_type", nullable = false)
    private ProviderType providerType;

    @Column(name = "base_url")
    private String baseUrl;

    @Column(name = "timeout_ms")
    private Integer timeoutMs;

    @Type(JsonBinaryType.class)
    @Column(name = "configuration", columnDefinition = "jsonb")
    private Map<String, Object> configuration;

    @Column(name = "sla_latency_ms")
    private Integer slaLatencyMs;

    @Column(name = "sla_success_rate")
    private Double slaSuccessRate;

    @Column(name = "sla_availability")
    private Double slaAvailability;

    @Column(name = "is_active")
    private boolean isActive;

    @Column(name = "is_healthy")
    private boolean isHealthy;

    @Type(JsonBinaryType.class)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, String> metadata;
}

