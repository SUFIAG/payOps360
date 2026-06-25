package com.payops.payops360.provider.adapter.in.rest.dto;

import com.payops.payops360.provider.domain.model.ProviderType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * DTO for provider registration request.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterProviderRequest {

    @NotBlank(message = "Provider ID is required")
    private String providerId;

    @NotBlank(message = "Provider name is required")
    private String providerName;

    @NotNull(message = "Provider type is required")
    private ProviderType providerType;

    private String baseUrl;
    private Integer timeoutMs;
    private Integer slaLatencyMs;
    private Double slaSuccessRate;
    private Double slaAvailability;
    private Map<String, Object> configuration;
    private Map<String, String> metadata;
}

