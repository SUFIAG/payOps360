package com.payops.payops360.payment.adapter.out.persistence;

import com.payops.payops360.payment.adapter.out.persistence.entity.PaymentEntity;
import com.payops.payops360.payment.adapter.out.persistence.entity.PaymentEventEntity;
import com.payops.payops360.payment.adapter.out.persistence.mapper.PaymentPersistenceMapper;
import com.payops.payops360.payment.adapter.out.persistence.repository.PaymentJpaRepository;
import com.payops.payops360.payment.application.port.out.PaymentRepositoryPort;
import com.payops.payops360.payment.domain.model.Payment;
import com.payops.payops360.payment.domain.model.PaymentEvent;
import com.payops.payops360.payment.domain.model.PaymentStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Payment Persistence Adapter - OUTPUT ADAPTER
 *
 * Implements the output port (PaymentRepositoryPort).
 * Adapts domain operations to JPA operations.
 *
 * This is the bridge between:
 * - Application layer (uses PaymentRepositoryPort interface)
 * - Persistence layer (JPA repositories and entities)
 *
 * Hexagonal architecture flow:
 * Domain Model → (via port) → Adapter → Entity → Database
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentPersistenceAdapter implements PaymentRepositoryPort {

    private final PaymentJpaRepository jpaRepository;
    private final PaymentPersistenceMapper mapper;

    @Override
    @Transactional
    public Payment save(Payment payment) {
        log.debug("Saving payment: paymentId={}", payment.getPaymentId());

        PaymentEntity entity;

        if (payment.getId() != null) {
            // Update existing
            entity = jpaRepository.findById(payment.getId())
                    .orElseThrow(() -> new IllegalStateException("Payment not found for update: " + payment.getId()));
            mapper.updateEntity(entity, payment);

            // Handle new events
            List<PaymentEvent> newEvents = payment.getEvents();
            if (newEvents != null && !newEvents.isEmpty()) {
                // Only add events that don't exist in entity
                int existingEventCount = entity.getEvents().size();
                for (int i = existingEventCount; i < newEvents.size(); i++) {
                    PaymentEvent domainEvent = newEvents.get(i);
                    PaymentEventEntity eventEntity = mapper.toEventEntity(domainEvent);
                    entity.addEvent(eventEntity);
                }
            }
        } else {
            // Create new
            entity = mapper.toEntity(payment);

            // Add events
            if (payment.getEvents() != null) {
                for (PaymentEvent event : payment.getEvents()) {
                    PaymentEventEntity eventEntity = mapper.toEventEntity(event);
                    entity.addEvent(eventEntity);
                }
            }
        }

        PaymentEntity savedEntity = jpaRepository.save(entity);
        return mapper.toDomain(savedEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Payment> findById(Long id) {
        log.debug("Finding payment by ID: {}", id);
        return jpaRepository.findById(id)
                .map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Payment> findByPaymentId(String paymentId) {
        log.debug("Finding payment by paymentId: {}", paymentId);
        return jpaRepository.findByPaymentId(paymentId)
                .map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Payment> findByExternalTransactionId(String externalTransactionId) {
        log.debug("Finding payment by externalTransactionId: {}", externalTransactionId);
        return jpaRepository.findByExternalTransactionId(externalTransactionId)
                .map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Payment> findAll(Pageable pageable) {
        log.debug("Finding all payments with pagination");
        return jpaRepository.findAll(pageable)
                .map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Payment> findByStatus(PaymentStatus status, Pageable pageable) {
        log.debug("Finding payments by status: {}", status);
        return jpaRepository.findByStatus(status, pageable)
                .map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Payment> findByProviderId(String providerId, Pageable pageable) {
        log.debug("Finding payments by providerId: {}", providerId);
        return jpaRepository.findByProviderId(providerId, pageable)
                .map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Payment> findByCustomerId(String customerId, Pageable pageable) {
        log.debug("Finding payments by customerId: {}", customerId);
        return jpaRepository.findByCustomerId(customerId, pageable)
                .map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Payment> findStuckPayments() {
        log.debug("Finding stuck payments");
        return jpaRepository.findStuckPayments().stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Payment> findRetryablePayments() {
        log.debug("Finding retryable payments");
        return jpaRepository.findRetryablePayments().stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Payment> findByCreatedAtBetween(Instant start, Instant end) {
        log.debug("Finding payments created between {} and {}", start, end);
        return jpaRepository.findByCreatedAtBetween(start, end).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long countByStatus(PaymentStatus status) {
        log.debug("Counting payments by status: {}", status);
        return jpaRepository.countByStatus(status);
    }

    @Override
    @Transactional(readOnly = true)
    public long countByProviderId(String providerId) {
        log.debug("Counting payments by providerId: {}", providerId);
        return jpaRepository.countByProviderId(providerId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByPaymentId(String paymentId) {
        log.debug("Checking if payment exists: paymentId={}", paymentId);
        return jpaRepository.existsByPaymentId(paymentId);
    }

    @Override
    @Transactional
    public void delete(Payment payment) {
        log.debug("Deleting payment: paymentId={}", payment.getPaymentId());
        if (payment.getId() != null) {
            jpaRepository.deleteById(payment.getId());
        }
    }
}

