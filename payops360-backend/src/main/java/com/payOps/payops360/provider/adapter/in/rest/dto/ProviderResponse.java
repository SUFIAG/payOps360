package com.payops.payops360.provider.adapter.in.rest.dto;

import com.payops.payops360.provider.domain.model.ProviderType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * DTO for provider response.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderResponse {

    private Long id;
    private String providerId;
    private String providerName;
    private ProviderType providerType;

    private String baseUrl;
    private Integer timeoutMs;
    private Map<String, Object> configuration;

    private Integer slaLatencyMs;
    private Double slaSuccessRate;
    private Double slaAvailability;

    private boolean isActive;
    private boolean isHealthy;

    private Map<String, String> metadata;

    private Instant createdAt;
    private Instant updatedAt;
}

