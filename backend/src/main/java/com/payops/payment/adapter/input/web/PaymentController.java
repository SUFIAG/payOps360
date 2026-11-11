package com.payops.payment.adapter.input.web;

import com.payops.payment.application.port.input.PaymentCommandUseCase;
import com.payops.payment.application.port.input.PaymentQueryUseCase;
import com.payops.payment.application.port.input.PaymentView;
import com.payops.payment.domain.PaymentMethod;
import com.payops.payment.domain.PaymentSearchCriteria;
import com.payops.payment.domain.PaymentStatus;
import com.payops.payment.domain.RiskAssessment;
import com.payops.shared.adapter.web.ApiResponse;
import com.payops.shared.adapter.web.AuthenticatedUser;
import com.payops.shared.adapter.web.ClientInfo;
import com.payops.shared.adapter.web.PageResponse;
import com.payops.shared.application.RequestContext;
import com.payops.shared.domain.PageQuery;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

@Tag(name = "Payments")
@Validated
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
class PaymentController {

    private final PaymentCommandUseCase commands;
    private final PaymentQueryUseCase queries;

    @GetMapping
    @PreAuthorize("hasAuthority('PAYMENT_READ')")
    ApiResponse<PageResponse<PaymentView>> search(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) Set<PaymentStatus> status,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) PaymentMethod method,
            @RequestParam(required = false) RiskAssessment.RiskLevel risk,
            @RequestParam(required = false) @Pattern(regexp = "^[A-Za-z]{3}$") String currency,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            @RequestParam(required = false) @Size(max = 100) String customer,
            @RequestParam(required = false) @Size(max = 100) String q,
            @RequestParam(required = false) @Pattern(regexp = "^[A-Za-z]{2}$") String country,
            @RequestParam(required = false) Boolean stuck,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size) {
        Long providerId = provider == null ? null
                : queries.providerIdFor(user.organizationId(), provider).orElse(-1L);
        PaymentSearchCriteria criteria = new PaymentSearchCriteria(status, providerId, method, risk, currency,
                minAmount, maxAmount, customer, q, country, stuck, from, to);
        return ApiResponse.ok(PageResponse.from(
                queries.search(user.organizationId(), criteria, PageQuery.of(page, size)), v -> v));
    }

    @GetMapping("/{reference}")
    @PreAuthorize("hasAuthority('PAYMENT_READ')")
    ApiResponse<PaymentQueryUseCase.PaymentDetails> get(@AuthenticationPrincipal AuthenticatedUser user,
                                                        @PathVariable String reference) {
        return ApiResponse.ok(queries.get(user.organizationId(), reference));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PAYMENT_WRITE')")
    ResponseEntity<ApiResponse<PaymentView>> ingest(@AuthenticationPrincipal AuthenticatedUser user,
                                                    @RequestHeader(name = "Idempotency-Key", required = false)
                                                    @Size(max = 100) String idempotencyKey,
                                                    @Valid @RequestBody IngestRequest request,
                                                    HttpServletRequest http) {
        var result = commands.ingest(user.actor(), request.toCommand(idempotencyKey), context(http));
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .header("Idempotent-Replayed", String.valueOf(result.replayed()))
                .body(ApiResponse.ok(result.payment()));
    }

    @PostMapping("/{reference}/status")
    @PreAuthorize("hasAuthority('PAYMENT_WRITE')")
    ApiResponse<PaymentView> changeStatus(@AuthenticationPrincipal AuthenticatedUser user,
                                          @PathVariable String reference,
                                          @Valid @RequestBody StatusChangeRequest request, HttpServletRequest http) {
        return ApiResponse.ok(commands.changeStatus(user.actor(), reference,
                new PaymentCommandUseCase.StatusChangeCommand(request.status(), request.reason(),
                        request.failureCode(), request.failureMessage(), request.latencyMs()), context(http)));
    }

    @PostMapping("/{reference}/retry")
    @PreAuthorize("hasAuthority('PAYMENT_WRITE')")
    ApiResponse<PaymentView> retry(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String reference,
                                   HttpServletRequest http) {
        return ApiResponse.ok(commands.retry(user.actor(), reference, context(http)));
    }

    private static RequestContext context(HttpServletRequest request) {
        ClientInfo info = ClientInfo.from(request);
        return new RequestContext(info.ipAddress(), info.userAgent());
    }

    record IngestRequest(
            @Size(max = 100) String merchantReference,
            @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal amount,
            @NotBlank @Pattern(regexp = "^[A-Za-z]{3}$") String currency,
            @NotBlank @Size(max = 60) String providerCode,
            @NotBlank @Size(max = 100) String customerRef,
            @NotNull PaymentMethod method,
            @Pattern(regexp = "^\\d{4}$") String cardLast4,
            @Pattern(regexp = "^[A-Za-z]{2}$") String country,
            PaymentStatus outcome,
            @Size(max = 60) String failureCode,
            @Size(max = 500) String failureMessage,
            @Min(0) @Max(600000) Integer latencyMs) {

        PaymentCommandUseCase.IngestCommand toCommand(String idempotencyKey) {
            return new PaymentCommandUseCase.IngestCommand(idempotencyKey, merchantReference, amount, currency,
                    providerCode, customerRef, method, cardLast4, country, outcome, failureCode, failureMessage, latencyMs);
        }
    }

    record StatusChangeRequest(@NotNull PaymentStatus status,
                               @Size(max = 500) String reason,
                               @Size(max = 60) String failureCode,
                               @Size(max = 500) String failureMessage,
                               @Min(0) @Max(600000) Integer latencyMs) {
    }
}
