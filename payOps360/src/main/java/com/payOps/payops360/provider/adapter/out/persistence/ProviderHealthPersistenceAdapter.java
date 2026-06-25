package com.payops.payops360.provider.adapter.out.persistence;

import com.payops.payops360.provider.adapter.out.persistence.entity.ProviderHealthEntity;
import com.payops.payops360.provider.adapter.out.persistence.mapper.ProviderPersistenceMapper;
import com.payops.payops360.provider.adapter.out.persistence.repository.ProviderHealthJpaRepository;
import com.payops.payops360.provider.application.port.out.ProviderHealthRepositoryPort;
import com.payops.payops360.provider.domain.model.ProviderHealth;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Provider Health Persistence Adapter - OUTPUT ADAPTER
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProviderHealthPersistenceAdapter implements ProviderHealthRepositoryPort {

    private final ProviderHealthJpaRepository jpaRepository;
    private final ProviderPersistenceMapper mapper;

    @Override
    @Transactional
    public ProviderHealth save(ProviderHealth health) {
        log.debug("Saving provider health: providerId={}", health.getProviderId());

        ProviderHealthEntity entity = mapper.toHealthEntity(health);
        ProviderHealthEntity saved = jpaRepository.save(entity);
        return mapper.toHealthDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProviderHealth> findLatestByProviderId(String providerId) {
        log.debug("Finding latest health for provider: {}", providerId);
        return jpaRepository.findLatestByProviderId(providerId)
                .map(mapper::toHealthDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProviderHealth> findByProviderIdAndTimeRange(
            String providerId,
            Instant start,
            Instant end) {

        log.debug("Finding health snapshots for provider {} between {} and {}",
                providerId, start, end);

        return jpaRepository.findByProviderIdAndSnapshotAtBetween(providerId, start, end)
                .stream()
                .map(mapper::toHealthDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProviderHealth> findRecentSnapshots(String providerId, int limit) {
        log.debug("Finding recent {} snapshots for provider: {}", limit, providerId);
        return jpaRepository.findRecentByProviderId(providerId, limit)
                .stream()
                .map(mapper::toHealthDomain)
                .toList();
    }
}

