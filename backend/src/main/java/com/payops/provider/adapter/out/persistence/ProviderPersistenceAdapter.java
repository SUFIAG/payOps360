package com.payops.provider.adapter.out.persistence;

import com.payops.provider.application.port.out.ProviderRepository;
import com.payops.provider.domain.Provider;
import com.payops.provider.domain.ProviderType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
class ProviderPersistenceAdapter implements ProviderRepository {

    private final ProviderJpaRepository jpa;

    @Override
    public List<Provider> findByOrganization(long organizationId) {
        return jpa.findByOrganizationIdOrderByNameAsc(organizationId).stream().map(ProviderPersistenceAdapter::toDomain).toList();
    }

    @Override
    public List<Provider> findAllActive() {
        return jpa.findByActiveTrue().stream().map(ProviderPersistenceAdapter::toDomain).toList();
    }

    @Override
    public Optional<Provider> findByCode(long organizationId, String code) {
        return jpa.findByOrganizationIdAndCode(organizationId, code).map(ProviderPersistenceAdapter::toDomain);
    }

    @Override
    public Optional<Provider> findById(long organizationId, long id) {
        return jpa.findByOrganizationIdAndId(organizationId, id).map(ProviderPersistenceAdapter::toDomain);
    }

    @Override
    public boolean existsByCode(long organizationId, String code) {
        return jpa.existsByOrganizationIdAndCode(organizationId, code);
    }

    @Override
    public Provider save(Provider provider) {
        ProviderEntity entity = provider.id() == null ? new ProviderEntity() : jpa.findById(provider.id()).orElseThrow();
        Instant now = Instant.now();
        if (entity.getId() == null) {
            entity.setOrganizationId(provider.organizationId());
            entity.setCode(provider.code());
            entity.setCreatedAt(now);
        }
        entity.setName(provider.name());
        entity.setType(provider.type().name());
        entity.setRegion(provider.region());
        entity.setSlaLatencyMs(provider.slaLatencyMs());
        entity.setSlaSuccessRate(provider.slaSuccessRate());
        entity.setCostBps(provider.costBps());
        entity.setActive(provider.active());
        entity.setUpdatedAt(now);
        return toDomain(jpa.save(entity));
    }

    private static Provider toDomain(ProviderEntity e) {
        return new Provider(e.getId(), e.getOrganizationId(), e.getCode(), e.getName(), ProviderType.valueOf(e.getType()),
                e.getRegion(), e.getSlaLatencyMs(), e.getSlaSuccessRate(), e.getCostBps(), e.isActive());
    }
}
