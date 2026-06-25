package com.payops.payops360.provider.adapter.out.persistence;

import com.payops.payops360.provider.adapter.out.persistence.entity.ProviderEntity;
import com.payops.payops360.provider.adapter.out.persistence.mapper.ProviderPersistenceMapper;
import com.payops.payops360.provider.adapter.out.persistence.repository.ProviderJpaRepository;
import com.payops.payops360.provider.application.port.out.ProviderRepositoryPort;
import com.payops.payops360.provider.domain.model.Provider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Provider Persistence Adapter - OUTPUT ADAPTER
 *
 * Implements the provider repository port.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProviderPersistenceAdapter implements ProviderRepositoryPort {

    private final ProviderJpaRepository jpaRepository;
    private final ProviderPersistenceMapper mapper;

    @Override
    @Transactional
    public Provider save(Provider provider) {
        log.debug("Saving provider: providerId={}", provider.getProviderId());

        ProviderEntity entity;

        if (provider.getId() != null) {
            // Update existing
            entity = jpaRepository.findById(provider.getId())
                    .orElseThrow(() -> new IllegalStateException("Provider not found: " + provider.getId()));
            mapper.updateEntity(entity, provider);
        } else {
            // Create new
            entity = mapper.toEntity(provider);
        }

        ProviderEntity saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Provider> findById(Long id) {
        log.debug("Finding provider by ID: {}", id);
        return jpaRepository.findById(id)
                .map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Provider> findByProviderId(String providerId) {
        log.debug("Finding provider by providerId: {}", providerId);
        return jpaRepository.findByProviderId(providerId)
                .map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Provider> findAll(Pageable pageable) {
        log.debug("Finding all providers");
        return jpaRepository.findAll(pageable)
                .map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Provider> findByIsActive(boolean isActive, Pageable pageable) {
        log.debug("Finding providers by isActive: {}", isActive);
        return jpaRepository.findByIsActive(isActive, pageable)
                .map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByProviderId(String providerId) {
        log.debug("Checking if provider exists: {}", providerId);
        return jpaRepository.existsByProviderId(providerId);
    }

    @Override
    @Transactional
    public void delete(Provider provider) {
        log.debug("Deleting provider: providerId={}", provider.getProviderId());
        if (provider.getId() != null) {
            jpaRepository.deleteById(provider.getId());
        }
    }
}

