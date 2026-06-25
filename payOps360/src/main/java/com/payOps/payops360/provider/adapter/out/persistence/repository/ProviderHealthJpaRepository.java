package com.payops.payops360.provider.adapter.out.persistence.repository;

import com.payops.payops360.provider.adapter.out.persistence.entity.ProviderHealthEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA Repository for ProviderHealthEntity.
 */
@Repository
public interface ProviderHealthJpaRepository extends JpaRepository<ProviderHealthEntity, Long> {

    @Query("SELECT h FROM ProviderHealthEntity h WHERE h.providerId = :providerId " +
           "ORDER BY h.snapshotAt DESC LIMIT 1")
    Optional<ProviderHealthEntity> findLatestByProviderId(@Param("providerId") String providerId);

    List<ProviderHealthEntity> findByProviderIdAndSnapshotAtBetween(
            String providerId,
            Instant start,
            Instant end
    );

    @Query("SELECT h FROM ProviderHealthEntity h WHERE h.providerId = :providerId " +
           "ORDER BY h.snapshotAt DESC LIMIT :limit")
    List<ProviderHealthEntity> findRecentByProviderId(
            @Param("providerId") String providerId,
            @Param("limit") int limit
    );
}

