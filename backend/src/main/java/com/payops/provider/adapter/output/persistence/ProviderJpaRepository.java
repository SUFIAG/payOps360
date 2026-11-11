package com.payops.provider.adapter.output.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface ProviderJpaRepository extends JpaRepository<ProviderEntity, Long> {

    List<ProviderEntity> findByOrganizationIdOrderByNameAsc(Long organizationId);

    List<ProviderEntity> findByActiveTrue();

    Optional<ProviderEntity> findByOrganizationIdAndCode(Long organizationId, String code);

    Optional<ProviderEntity> findByOrganizationIdAndId(Long organizationId, Long id);

    boolean existsByOrganizationIdAndCode(Long organizationId, String code);
}
