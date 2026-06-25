package com.payops.payops360.provider.application.port.in;

import com.payops.payops360.provider.domain.model.Provider;
import com.payops.payops360.provider.domain.model.ProviderType;

import java.util.Map;

/**
 * INPUT PORT - Register provider use case
 */
public interface RegisterProviderUseCase {

    Provider register(RegisterProviderCommand command);

    record RegisterProviderCommand(
            String providerId,
            String providerName,
            ProviderType providerType,
            String baseUrl,
            Integer timeoutMs,
            Integer slaLatencyMs,
            Double slaSuccessRate,
            Double slaAvailability,
            Map<String, Object> configuration,
            Map<String, String> metadata
    ) {
        public RegisterProviderCommand {
            if (providerId == null || providerId.isBlank()) {
                throw new IllegalArgumentException("Provider ID cannot be null or empty");
            }
            if (providerName == null || providerName.isBlank()) {
                throw new IllegalArgumentException("Provider name cannot be null or empty");
            }
            if (providerType == null) {
                throw new IllegalArgumentException("Provider type cannot be null");
            }
        }
    }
}

