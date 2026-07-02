package com.payops.payops360.provider.application.service;

import com.payops.payops360.common.exception.ResourceNotFoundException;
import com.payops.payops360.provider.application.port.in.*;
import com.payops.payops360.provider.application.port.out.ProviderHealthRepositoryPort;
import com.payops.payops360.provider.application.port.out.ProviderRepositoryPort;
import com.payops.payops360.provider.domain.model.Provider;
import com.payops.payops360.provider.domain.model.ProviderHealth;
import com.payops.payops360.provider.domain.service.ProviderHealthCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Provider Application Service
 *
 * Implements all provider use cases.
 * Coordinates between domain logic and persistence.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ProviderService implements
        RegisterProviderUseCase,
        GetProviderUseCase,
        ListProvidersUseCase {

    private final ProviderRepositoryPort providerRepository;
    private final ProviderHealthRepositoryPort healthRepository;
    private final ProviderHealthCalculator healthCalculator;

    @Override
    public Provider register(RegisterProviderCommand command) {
        log.info("Registering provider: providerId={}, name={}",
                command.providerId(), command.providerName());

        // Check if already exists
        if (providerRepository.existsByProviderId(command.providerId())) {
            throw new IllegalStateException("Provider already exists: " + command.providerId());
        }

        // Create domain object
        Provider provider = Provider.builder()
                .providerId(command.providerId())
                .providerName(command.providerName())
                .providerType(command.providerType())
                .baseUrl(command.baseUrl())
                .timeoutMs(command.timeoutMs())
                .slaLatencyMs(command.slaLatencyMs())
                .slaSuccessRate(command.slaSuccessRate())
                .slaAvailability(command.slaAvailability())
                .configuration(command.configuration() != null ? command.configuration() : java.util.Map.of())
                .metadata(command.metadata() != null ? command.metadata() : java.util.Map.of())
                .isActive(true)
                .isHealthy(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        Provider saved = providerRepository.save(provider);
        log.info("Provider registered successfully: providerId={}", saved.getProviderId());

        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public Provider getById(Long id) {
        log.debug("Fetching provider by ID: {}", id);
        return providerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Provider", id.toString()));
    }

    @Override
    @Transactional(readOnly = true)
    public Provider getByProviderId(String providerId) {
        log.debug("Fetching provider by providerId: {}", providerId);
        return providerRepository.findByProviderId(providerId)
                .orElseThrow(() -> new ResourceNotFoundException("Provider", providerId));
    }

    @Override
    @Transactional(readOnly = true)
    public ProviderHealth getHealth(String providerId) {
        log.debug("Fetching health for provider: {}", providerId);

        // Verify provider exists
        Provider provider = getByProviderId(providerId);

        // Get latest health snapshot
        return healthRepository.findLatestByProviderId(providerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Provider health data not available for: " + providerId));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Provider> listAll(Pageable pageable) {
        log.debug("Listing all providers");
        return providerRepository.findAll(pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Provider> listActive(Pageable pageable) {
        log.debug("Listing active providers");
        return providerRepository.findByIsActive(true, pageable);
    }
}

