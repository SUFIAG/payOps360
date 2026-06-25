package com.payops.payops360.provider.adapter.out.persistence.repository;

import com.payops.payops360.provider.adapter.out.persistence.entity.ProviderEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA Repository for ProviderEntity.
 */
@Repository
public interface ProviderJpaRepository extends JpaRepository<ProviderEntity, Long> {

    Optional<ProviderEntity> findByProviderId(String providerId);

    Page<ProviderEntity> findByIsActive(boolean isActive, Pageable pageable);

    boolean existsByProviderId(String providerId);
}

