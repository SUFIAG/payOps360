package com.payops.incident.adapter.out.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

interface IncidentJpaRepository extends JpaRepository<IncidentEntity, Long> {

    Optional<IncidentEntity> findByOrganizationIdAndReference(Long organizationId, String reference);

    Optional<IncidentEntity> findByOrganizationIdAndId(Long organizationId, Long id);

    @Query("""
            select i from IncidentEntity i where i.organizationId = :org and i.status <> 'RESOLVED'
              and ((:provider is null and i.providerId is null) or i.providerId = :provider)
              and i.openedAt > :after order by i.openedAt desc
            """)
    List<IncidentEntity> findActiveForProvider(@Param("org") Long org, @Param("provider") Long provider,
                                               @Param("after") Instant after, Pageable pageable);

    @Query("select i from IncidentEntity i where i.organizationId = :org and (:status is null or i.status = :status) order by i.openedAt desc")
    Page<IncidentEntity> search(@Param("org") Long org, @Param("status") String status, Pageable pageable);

    List<IncidentEntity> findByOrganizationIdAndStatusNot(Long organizationId, String status);

    List<IncidentEntity> findByOrganizationIdAndStatusAndResolvedAtAfter(Long organizationId, String status, Instant since);

    @Query("select i from IncidentEntity i where i.status = 'OPEN' order by i.openedAt")
    List<IncidentEntity> findAwaitingAcknowledgement(Pageable pageable);

    @Query("select i.status, count(i) from IncidentEntity i where i.organizationId = :org group by i.status")
    List<Object[]> countByStatus(@Param("org") Long org);
}
