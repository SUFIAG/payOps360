package com.payops.payops360.payment.adapter.out.persistence.repository;

import com.payops.payops360.payment.adapter.out.persistence.entity.PaymentEntity;
import com.payops.payops360.payment.domain.model.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA Repository for PaymentEntity.
 *
 * This is part of the OUTPUT ADAPTER (Persistence layer).
 * Provides database operations for payments.
 */
@Repository
public interface PaymentJpaRepository extends JpaRepository<PaymentEntity, Long> {

    Optional<PaymentEntity> findByPaymentId(String paymentId);

    Optional<PaymentEntity> findByExternalTransactionId(String externalTransactionId);

    Page<PaymentEntity> findByStatus(PaymentStatus status, Pageable pageable);

    Page<PaymentEntity> findByProviderId(String providerId, Pageable pageable);

    Page<PaymentEntity> findByCustomerId(String customerId, Pageable pageable);

    @Query("SELECT p FROM PaymentEntity p WHERE p.isStuck = true")
    List<PaymentEntity> findStuckPayments();

    @Query("SELECT p FROM PaymentEntity p WHERE p.status = 'FAILED' AND p.retryCount < 3")
    List<PaymentEntity> findRetryablePayments();

    List<PaymentEntity> findByCreatedAtBetween(Instant start, Instant end);

    long countByStatus(PaymentStatus status);

    long countByProviderId(String providerId);

    boolean existsByPaymentId(String paymentId);
}

