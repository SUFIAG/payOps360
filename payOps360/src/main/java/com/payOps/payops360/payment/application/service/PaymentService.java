package com.payops.payops360.payment.application.service;

import com.payops.payops360.common.exception.ResourceNotFoundException;
import com.payops.payops360.payment.application.port.in.*;
import com.payops.payops360.payment.application.port.out.PaymentRepositoryPort;
import com.payops.payops360.payment.domain.model.Payment;
import com.payops.payops360.payment.domain.model.PaymentStatus;
import com.payops.payops360.payment.domain.service.PaymentLifecycleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Payment Application Service
 *
 * Implements all payment use cases (input ports).
 * Orchestrates domain logic and persistence operations.
 *
 * This is the APPLICATION layer in hexagonal architecture.
 * It coordinates between domain logic and external systems.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PaymentService implements
        IngestPaymentUseCase,
        GetPaymentUseCase,
        ListPaymentsUseCase,
        UpdatePaymentStatusUseCase {

    private final PaymentRepositoryPort paymentRepository;
    private final PaymentLifecycleService lifecycleService;

    /**
     * Ingest a new payment
     */
    @Override
    public Payment ingest(IngestPaymentCommand command) {
        log.info("Ingesting payment: provider={}, amount={}",
                command.providerId(), command.amount());

        // Generate unique payment ID
        String paymentId = generatePaymentId();

        // Create domain object
        Payment payment = Payment.builder()
                .paymentId(paymentId)
                .externalTransactionId(command.externalTransactionId())
                .merchantReference(command.merchantReference())
                .amount(command.amount())
                .providerId(command.providerId())
                .providerName(command.providerName())
                .providerTransactionId(command.providerTransactionId())
                .customerId(command.customerId())
                .paymentMethodType(command.paymentMethodType())
                .paymentMethodLast4(command.paymentMethodLast4())
                .status(PaymentStatus.INITIATED)
                .metadata(command.metadata() != null ? command.metadata() : java.util.Map.of())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .statusChangedAt(Instant.now())
                .build();

        // Persist
        Payment savedPayment = paymentRepository.save(payment);

        log.info("Payment ingested successfully: paymentId={}", savedPayment.getPaymentId());
        return savedPayment;
    }

    /**
     * Get payment by database ID
     */
    @Override
    @Transactional(readOnly = true)
    public Payment getById(Long id) {
        log.debug("Fetching payment by ID: {}", id);
        return paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", id.toString()));
    }

    /**
     * Get payment by business identifier
     */
    @Override
    @Transactional(readOnly = true)
    public Payment getByPaymentId(String paymentId) {
        log.debug("Fetching payment by paymentId: {}", paymentId);
        return paymentRepository.findByPaymentId(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));
    }

    /**
     * List all payments
     */
    @Override
    @Transactional(readOnly = true)
    public Page<Payment> listAll(Pageable pageable) {
        log.debug("Listing all payments: page={}, size={}",
                pageable.getPageNumber(), pageable.getPageSize());
        return paymentRepository.findAll(pageable);
    }

    /**
     * List payments by status
     */
    @Override
    @Transactional(readOnly = true)
    public Page<Payment> listByStatus(PaymentStatus status, Pageable pageable) {
        log.debug("Listing payments by status: status={}", status);
        return paymentRepository.findByStatus(status, pageable);
    }

    /**
     * List payments by provider
     */
    @Override
    @Transactional(readOnly = true)
    public Page<Payment> listByProvider(String providerId, Pageable pageable) {
        log.debug("Listing payments by provider: providerId={}", providerId);
        return paymentRepository.findByProviderId(providerId, pageable);
    }

    /**
     * List payments by customer
     */
    @Override
    @Transactional(readOnly = true)
    public Page<Payment> listByCustomer(String customerId, Pageable pageable) {
        log.debug("Listing payments by customer: customerId={}", customerId);
        return paymentRepository.findByCustomerId(customerId, pageable);
    }

    /**
     * Update payment status
     */
    @Override
    public Payment updateStatus(UpdateStatusCommand command) {
        log.info("Updating payment status: paymentId={}, newStatus={}",
                command.paymentId(), command.newStatus());

        // Fetch existing payment
        Payment payment = getByPaymentId(command.paymentId());

        // Validate transition using domain service
        if (!lifecycleService.canTransition(payment, command.newStatus())) {
            throw new IllegalStateException(
                    String.format("Cannot transition from %s to %s",
                            payment.getStatus(), command.newStatus()));
        }

        // Perform state transition (domain logic)
        payment.transitionTo(command.newStatus(), command.reason());

        // Clear stuck status if payment progressed
        if (payment.isStuck() && command.newStatus() != payment.getPreviousStatus()) {
            payment.clearStuckStatus();
        }

        // Persist
        Payment updatedPayment = paymentRepository.save(payment);

        log.info("Payment status updated successfully: paymentId={}, status={}",
                updatedPayment.getPaymentId(), updatedPayment.getStatus());

        return updatedPayment;
    }

    /**
     * Generate unique payment ID
     */
    private String generatePaymentId() {
        return "PAY-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
    }
}

