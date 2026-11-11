package com.payops.payment.adapter.output.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

interface PaymentJpaRepository extends JpaRepository<PaymentEntity, Long>, JpaSpecificationExecutor<PaymentEntity> {

    Optional<PaymentEntity> findByOrganizationIdAndReference(Long organizationId, String reference);

    Optional<PaymentEntity> findByOrganizationIdAndIdempotencyKey(Long organizationId, String idempotencyKey);

    @Query("select p from PaymentEntity p where p.status = :status and p.stuck = false and p.statusChangedAt < :before order by p.statusChangedAt")
    List<PaymentEntity> findStuckCandidates(@Param("status") String status, @Param("before") Instant before, Pageable pageable);
}
