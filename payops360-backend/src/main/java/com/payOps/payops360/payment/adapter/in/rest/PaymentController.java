package com.payops.payops360.payment.adapter.in.rest;

import com.payops.payops360.common.dto.ApiResponse;
import com.payops.payops360.common.dto.PagedResponse;
import com.payops.payops360.payment.adapter.in.rest.dto.*;
import com.payops.payops360.payment.adapter.in.rest.mapper.PaymentRestMapper;
import com.payops.payops360.payment.application.port.in.*;
import com.payops.payops360.payment.domain.model.Payment;
import com.payops.payops360.payment.domain.model.PaymentStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Payment REST Controller - INPUT ADAPTER
 *
 * This is part of the hexagonal architecture's ADAPTER layer.
 * It adapts HTTP/REST requests to use case calls.
 *
 * Responsibilities:
 * - Validate HTTP requests
 * - Map DTOs to domain commands
 * - Call use cases (input ports)
 * - Map domain responses to DTOs
 * - Handle HTTP-specific concerns
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Payment lifecycle management APIs")
public class PaymentController {

    private final IngestPaymentUseCase ingestPaymentUseCase;
    private final GetPaymentUseCase getPaymentUseCase;
    private final ListPaymentsUseCase listPaymentsUseCase;
    private final UpdatePaymentStatusUseCase updatePaymentStatusUseCase;
    private final PaymentRestMapper mapper;

    /**
     * Ingest a new payment
     */
    @PostMapping
    @Operation(summary = "Ingest Payment", description = "Create a new payment in the system")
    public ResponseEntity<ApiResponse<PaymentResponse>> ingestPayment(
            @Valid @RequestBody IngestPaymentRequest request) {

        log.info("Received payment ingestion request: providerId={}, amount={}",
                request.getProviderId(), request.getAmount());

        // Map DTO to command
        IngestPaymentUseCase.IngestPaymentCommand command = mapper.toCommand(request);

        // Execute use case
        Payment payment = ingestPaymentUseCase.ingest(command);

        // Map domain to response DTO
        PaymentResponse response = mapper.toResponse(payment);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    /**
     * Get payment by ID
     */
    @GetMapping("/{paymentId}")
    @Operation(summary = "Get Payment", description = "Retrieve payment by business ID")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPayment(
            @PathVariable @Parameter(description = "Payment business ID") String paymentId) {

        log.debug("Fetching payment: paymentId={}", paymentId);

        Payment payment = getPaymentUseCase.getByPaymentId(paymentId);
        PaymentResponse response = mapper.toResponse(payment);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Get payment timeline
     */
    @GetMapping("/{paymentId}/timeline")
    @Operation(summary = "Get Payment Timeline", description = "Retrieve payment event timeline")
    public ResponseEntity<ApiResponse<List<PaymentEventResponse>>> getPaymentTimeline(
            @PathVariable String paymentId) {

        log.debug("Fetching payment timeline: paymentId={}", paymentId);

        Payment payment = getPaymentUseCase.getByPaymentId(paymentId);
        List<PaymentEventResponse> timeline = payment.getTimeline().stream()
                .map(PaymentEventResponse::from)
                .toList();

        return ResponseEntity.ok(ApiResponse.success(timeline));
    }

    /**
     * List all payments with pagination
     */
    @GetMapping
    @Operation(summary = "List Payments", description = "List all payments with pagination and filtering")
    public ResponseEntity<ApiResponse<PagedResponse<PaymentResponse>>> listPayments(
            @RequestParam(required = false) @Parameter(description = "Filter by status") PaymentStatus status,
            @RequestParam(required = false) @Parameter(description = "Filter by provider ID") String providerId,
            @RequestParam(required = false) @Parameter(description = "Filter by customer ID") String customerId,
            @RequestParam(defaultValue = "0") @Parameter(description = "Page number") int page,
            @RequestParam(defaultValue = "20") @Parameter(description = "Page size") int size,
            @RequestParam(defaultValue = "createdAt") @Parameter(description = "Sort field") String sortBy,
            @RequestParam(defaultValue = "DESC") @Parameter(description = "Sort direction") Sort.Direction direction) {

        log.debug("Listing payments: status={}, providerId={}, customerId={}, page={}, size={}",
                status, providerId, customerId, page, size);

        // Create pageable
        Pageable pageable = PageRequest.of(page, Math.min(size, 100), Sort.by(direction, sortBy));

        // Execute appropriate use case
        Page<Payment> payments;
        if (status != null) {
            payments = listPaymentsUseCase.listByStatus(status, pageable);
        } else if (providerId != null) {
            payments = listPaymentsUseCase.listByProvider(providerId, pageable);
        } else if (customerId != null) {
            payments = listPaymentsUseCase.listByCustomer(customerId, pageable);
        } else {
            payments = listPaymentsUseCase.listAll(pageable);
        }

        // Map to response
        Page<PaymentResponse> responsePage = payments.map(mapper::toResponse);
        PagedResponse<PaymentResponse> pagedResponse = PagedResponse.from(responsePage);

        return ResponseEntity.ok(ApiResponse.success(pagedResponse));
    }

    /**
     * Update payment status
     */
    @PatchMapping("/{paymentId}/status")
    @Operation(summary = "Update Payment Status", description = "Update payment status with validation")
    public ResponseEntity<ApiResponse<PaymentResponse>> updatePaymentStatus(
            @PathVariable String paymentId,
            @Valid @RequestBody UpdatePaymentStatusRequest request) {

        log.info("Updating payment status: paymentId={}, newStatus={}",
                paymentId, request.getNewStatus());

        // Map to command
        UpdatePaymentStatusUseCase.UpdateStatusCommand command =
                mapper.toUpdateCommand(paymentId, request);

        // Execute use case
        Payment payment = updatePaymentStatusUseCase.updateStatus(command);

        // Map to response
        PaymentResponse response = mapper.toResponse(payment);

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}

