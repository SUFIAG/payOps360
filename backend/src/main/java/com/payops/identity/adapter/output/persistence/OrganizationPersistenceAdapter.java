package com.payops.identity.adapter.output.persistence;

import com.payops.identity.application.port.output.OrganizationRepository;
import com.payops.identity.domain.Organization;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
class OrganizationPersistenceAdapter implements OrganizationRepository {

    private final OrganizationJpaRepository jpa;

    @Override
    public Optional<Organization> findById(long id) {
        return jpa.findById(id).map(OrganizationPersistenceAdapter::toDomain);
    }

    @Override
    public List<Organization> findAllActive() {
        return jpa.findByStatus("ACTIVE").stream().map(OrganizationPersistenceAdapter::toDomain).toList();
    }

    @Override
    public Organization save(Organization organization) {
        OrganizationEntity entity = organization.id() == null
                ? new OrganizationEntity()
                : jpa.findById(organization.id()).orElseThrow();
        Instant now = Instant.now();
        if (entity.getId() == null) {
            entity.setCreatedAt(now);
            entity.setStatus("ACTIVE");
        }
        entity.setPublicId(organization.publicId());
        entity.setName(organization.name());
        entity.setSlug(organization.slug());
        entity.setMfaRequired(organization.mfaRequired());
        entity.setAiEnabled(organization.aiEnabled());
        entity.setBaseCurrency(organization.baseCurrency());
        entity.setUpdatedAt(now);
        return toDomain(jpa.save(entity));
    }

    @Override
    public long count() {
        return jpa.count();
    }

    @Override
    public boolean existsBySlug(String slug) {
        return jpa.existsBySlug(slug);
    }

    private static Organization toDomain(OrganizationEntity e) {
        return new Organization(e.getId(), e.getPublicId(), e.getName(), e.getSlug(), e.isMfaRequired(),
                e.isAiEnabled(), e.getBaseCurrency());
    }
}
