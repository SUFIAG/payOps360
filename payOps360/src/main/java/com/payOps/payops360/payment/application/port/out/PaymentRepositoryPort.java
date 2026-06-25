package com.payops.payops360.payment.application.port.out;

import com.payops.payops360.payment.domain.model.Payment;
import com.payops.payops360.payment.domain.model.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * OUTPUT PORT (Repository Interface)
 *
 * Defines the contract for payment persistence operations.
 * This is part of the hexagonal architecture's "ports" layer.
 *
 * Implementation will be in the adapter layer (JPA).
 * No framework-specific types in method signatures (except Spring's Page/Pageable for pragmatism).
 */
public interface PaymentRepositoryPort {

    /**
     * Save a payment
     */
    Payment save(Payment payment);

    /**
     * Find payment by ID
     */
    Optional<Payment> findById(Long id);

    /**
     * Find payment by business identifier (payment ID)
     */
    Optional<Payment> findByPaymentId(String paymentId);

    /**
     * Find payment by external transaction ID
     */
    Optional<Payment> findByExternalTransactionId(String externalTransactionId);

    /**
     * Find all payments with pagination
     */
    Page<Payment> findAll(Pageable pageable);

    /**
     * Find payments by status
     */
    Page<Payment> findByStatus(PaymentStatus status, Pageable pageable);

    /**
     * Find payments by provider ID
     */
    Page<Payment> findByProviderId(String providerId, Pageable pageable);

    /**
     * Find payments by customer ID
     */
    Page<Payment> findByCustomerId(String customerId, Pageable pageable);

    /**
     * Find stuck payments
     */
    List<Payment> findStuckPayments();

    /**
     * Find failed payments that can be retried
     */
    List<Payment> findRetryablePayments();

    /**
     * Find payments created within a time range
     */
    List<Payment> findByCreatedAtBetween(Instant start, Instant end);

    /**
     * Count payments by status
     */
    long countByStatus(PaymentStatus status);

    /**
     * Count payments by provider ID
     */
    long countByProviderId(String providerId);

    /**
     * Check if payment exists by payment ID
     */
    boolean existsByPaymentId(String paymentId);

    /**
     * Delete payment (for testing purposes only)
     */
    void delete(Payment payment);
}

