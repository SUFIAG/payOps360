package com.payops.alert.adapter.out.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

interface AlertJpaRepository extends JpaRepository<AlertEntity, Long> {

    Optional<AlertEntity> findByOrganizationIdAndReference(Long organizationId, String reference);

    Optional<AlertEntity> findFirstByOrganizationIdAndFingerprintAndStatusNot(Long organizationId, String fingerprint, String status);

    @Query("""
            select a from AlertEntity a where a.organizationId = :org
              and (:status is null or a.status = :status)
              and (:severity is null or a.severity = :severity)
            order by a.lastSeenAt desc
            """)
    Page<AlertEntity> search(@Param("org") Long org, @Param("status") String status,
                             @Param("severity") String severity, Pageable pageable);

    @Query("select a from AlertEntity a where a.status <> 'RESOLVED' and a.lastSeenAt < :before order by a.lastSeenAt")
    List<AlertEntity> findQuiet(@Param("before") Instant before, Pageable pageable);

    @Query("select a from AlertEntity a where a.status = 'OPEN' and a.severity in :severities order by a.detectedAt")
    List<AlertEntity> findAwaitingAcknowledgement(@Param("severities") List<String> severities, Pageable pageable);

    @Query("select a.status, count(a) from AlertEntity a where a.organizationId = :org group by a.status")
    List<Object[]> countByStatus(@Param("org") Long org);

    @Query("select a.severity, count(a) from AlertEntity a where a.organizationId = :org and a.status <> 'RESOLVED' group by a.severity")
    List<Object[]> countUnresolvedBySeverity(@Param("org") Long org);

    long countByOrganizationIdAndStatusAndResolvedAtAfter(Long organizationId, String status, Instant since);
}
