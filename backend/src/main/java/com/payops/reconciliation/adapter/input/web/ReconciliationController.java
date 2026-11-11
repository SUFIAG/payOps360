package com.payops.reconciliation.adapter.input.web;

import com.payops.reconciliation.application.port.input.BatchView;
import com.payops.reconciliation.application.port.input.ExceptionCommandUseCase;
import com.payops.reconciliation.application.port.input.ExceptionView;
import com.payops.reconciliation.application.port.input.FeeScheduleUseCase;
import com.payops.reconciliation.application.port.input.ReconciliationQueryUseCase;
import com.payops.reconciliation.application.port.input.SettlementImportUseCase;
import com.payops.reconciliation.domain.ExceptionStatus;
import com.payops.reconciliation.domain.ExceptionType;
import com.payops.reconciliation.domain.MappingProfile;
import com.payops.reconciliation.domain.ResolutionReason;
import com.payops.shared.adapter.web.ApiResponse;
import com.payops.shared.adapter.web.AuthenticatedUser;
import com.payops.shared.adapter.web.ClientInfo;
import com.payops.shared.adapter.web.PageResponse;
import com.payops.shared.application.RequestContext;
import com.payops.shared.domain.DomainException;
import com.payops.shared.domain.PageQuery;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Tag(name = "Reconciliation")
@Validated
@RestController
@RequestMapping("/api/v1/reconciliation")
@RequiredArgsConstructor
class ReconciliationController {

    private static final Set<String> CSV_TYPES = Set.of("text/csv", "text/plain", "application/csv",
            "application/vnd.ms-excel", "application/octet-stream");

    private final SettlementImportUseCase imports;
    private final FeeScheduleUseCase feeSchedules;
    private final ReconciliationQueryUseCase queries;
    private final ExceptionCommandUseCase exceptions;

    // ------------------------------------------------------------------ KPIs

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('RECON_READ')")
    ApiResponse<ReconciliationQueryUseCase.Summary> summary(@AuthenticationPrincipal AuthenticatedUser user,
                                                            @RequestParam(defaultValue = "30") @Min(1) @Max(365) int days) {
        return ApiResponse.ok(queries.summary(user.organizationId(), days));
    }

    @GetMapping("/trend")
    @PreAuthorize("hasAuthority('RECON_READ')")
    ApiResponse<List<ReconciliationQueryUseCase.TrendPoint>> trend(@AuthenticationPrincipal AuthenticatedUser user,
                                                                   @RequestParam(defaultValue = "30") @Min(1) @Max(365) int days) {
        return ApiResponse.ok(queries.matchRateTrend(user.organizationId(), days));
    }

    // ------------------------------------------------------------------ batches

    @GetMapping("/batches")
    @PreAuthorize("hasAuthority('RECON_READ')")
    ApiResponse<PageResponse<BatchView>> batches(@AuthenticationPrincipal AuthenticatedUser user,
                                                 @RequestParam(required = false) @Size(max = 60) String provider,
                                                 @RequestParam(defaultValue = "0") @Min(0) int page,
                                                 @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.ok(PageResponse.from(queries.batches(user.organizationId(), provider, PageQuery.of(page, size)),
                v -> v));
    }

    @GetMapping("/batches/{reference}")
    @PreAuthorize("hasAuthority('RECON_READ')")
    ApiResponse<BatchView> batch(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String reference) {
        return ApiResponse.ok(queries.batch(user.organizationId(), reference));
    }

    @PostMapping(path = "/batches", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('RECON_MANAGE')")
    ResponseEntity<ApiResponse<BatchView>> upload(@AuthenticationPrincipal AuthenticatedUser user,
                                                  @RequestParam("provider") @NotBlank @Size(max = 60) String provider,
                                                  @RequestPart("file") MultipartFile file, HttpServletRequest http) {
        if (file.isEmpty()) {
            throw new DomainException("INVALID_SETTLEMENT_FILE", "The uploaded file is empty");
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        String type = file.getContentType() == null ? "application/octet-stream"
                : file.getContentType().toLowerCase(Locale.ROOT).split(";")[0].trim();
        if (!(name.endsWith(".csv") || name.endsWith(".txt")) || !CSV_TYPES.contains(type)) {
            throw new DomainException("UNSUPPORTED_FILE_TYPE", "Upload a CSV settlement file (.csv)");
        }
        try (InputStream content = file.getInputStream()) {
            BatchView batch = imports.importSettlement(user.actor(), provider, file.getOriginalFilename(), content,
                    context(http));
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.ok(batch));
        } catch (IOException e) {
            throw new DomainException("INVALID_SETTLEMENT_FILE", "The uploaded file could not be read");
        }
    }

    @GetMapping("/providers/{code}/sample-csv")
    @PreAuthorize("hasAuthority('RECON_MANAGE')")
    ResponseEntity<byte[]> sample(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String code) {
        SettlementImportUseCase.SampleFile sample = imports.sampleFile(user.actor(), code);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(sample.fileName()).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(sample.content().getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/providers/{code}/mapping")
    @PreAuthorize("hasAuthority('RECON_READ')")
    ApiResponse<SettlementImportUseCase.MappingView> mapping(@AuthenticationPrincipal AuthenticatedUser user,
                                                             @PathVariable String code) {
        return ApiResponse.ok(imports.mapping(user.organizationId(), code));
    }

    @PutMapping("/providers/{code}/mapping")
    @PreAuthorize("hasAuthority('RECON_MANAGE')")
    ApiResponse<SettlementImportUseCase.MappingView> updateMapping(@AuthenticationPrincipal AuthenticatedUser user,
                                                                   @PathVariable String code,
                                                                   @Valid @RequestBody MappingRequest request,
                                                                   HttpServletRequest http) {
        return ApiResponse.ok(imports.updateMapping(user.actor(), code,
                new SettlementImportUseCase.MappingCommand(request.delimiter(), request.dateFormat(), request.columns()),
                context(http)));
    }

    // ------------------------------------------------------------------ fee schedules

    @GetMapping("/providers/{code}/fee-schedule")
    @PreAuthorize("hasAuthority('RECON_READ')")
    ApiResponse<FeeScheduleUseCase.FeeScheduleView> feeSchedule(@AuthenticationPrincipal AuthenticatedUser user,
                                                                @PathVariable String code) {
        return ApiResponse.ok(feeSchedules.feeSchedule(user.organizationId(), code));
    }

    @PutMapping("/providers/{code}/fee-schedule")
    @PreAuthorize("hasAuthority('RECON_MANAGE')")
    ApiResponse<FeeScheduleUseCase.FeeScheduleView> replaceFeeSchedule(@AuthenticationPrincipal AuthenticatedUser user,
                                                                       @PathVariable String code,
                                                                       @Valid @RequestBody FeeScheduleRequest request,
                                                                       HttpServletRequest http) {
        return ApiResponse.ok(feeSchedules.replaceFeeSchedule(user.actor(), code, request.rules().stream()
                .map(r -> new FeeScheduleUseCase.FeeRuleView(blankToNull(r.method()), blankToNull(r.currency()),
                        r.percentage(), r.fixedFee()))
                .toList(), context(http)));
    }

    // ------------------------------------------------------------------ exceptions

    @GetMapping("/exceptions")
    @PreAuthorize("hasAuthority('RECON_READ')")
    ApiResponse<PageResponse<ExceptionView>> exceptions(@AuthenticationPrincipal AuthenticatedUser user,
                                                        @RequestParam(required = false) Set<ExceptionType> type,
                                                        @RequestParam(required = false) Set<ExceptionStatus> status,
                                                        @RequestParam(required = false) @Size(max = 60) String provider,
                                                        @RequestParam(required = false) @Size(max = 40) String batch,
                                                        @RequestParam(required = false) @Size(max = 320) String assignee,
                                                        @RequestParam(defaultValue = "0") @Min(0) int page,
                                                        @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size) {
        return ApiResponse.ok(PageResponse.from(queries.exceptions(user.organizationId(),
                new ReconciliationQueryUseCase.ExceptionFilter(type, status, provider, batch, assignee),
                PageQuery.of(page, size)), v -> v));
    }

    @GetMapping("/exceptions/{reference}")
    @PreAuthorize("hasAuthority('RECON_READ')")
    ApiResponse<ReconciliationQueryUseCase.ExceptionDetails> exception(@AuthenticationPrincipal AuthenticatedUser user,
                                                                       @PathVariable String reference) {
        return ApiResponse.ok(queries.exception(user.organizationId(), reference));
    }

    @PostMapping("/exceptions/{reference}/assign")
    @PreAuthorize("hasAuthority('RECON_MANAGE')")
    ApiResponse<ExceptionView> assign(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String reference,
                                      @Valid @RequestBody AssignRequest request, HttpServletRequest http) {
        return ApiResponse.ok(exceptions.assign(user.actor(), reference, request.assignee(), context(http)));
    }

    @PostMapping("/exceptions/{reference}/resolve")
    @PreAuthorize("hasAuthority('RECON_MANAGE')")
    ApiResponse<ExceptionView> resolve(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String reference,
                                       @Valid @RequestBody ResolveRequest request, HttpServletRequest http) {
        return ApiResponse.ok(exceptions.resolve(user.actor(), reference, request.reason(), request.note(), context(http)));
    }

    @PostMapping("/exceptions/{reference}/match")
    @PreAuthorize("hasAuthority('RECON_MANAGE')")
    ApiResponse<ExceptionView> match(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String reference,
                                     @Valid @RequestBody MatchRequest request, HttpServletRequest http) {
        return ApiResponse.ok(exceptions.manualMatch(user.actor(), reference, request.paymentReference().trim(),
                request.note(), context(http)));
    }

    @PostMapping("/exceptions/{reference}/write-off")
    @PreAuthorize("hasAuthority('RECON_MANAGE')")
    ApiResponse<ExceptionView> writeOff(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String reference,
                                        @Valid @RequestBody NoteRequest request, HttpServletRequest http) {
        return ApiResponse.ok(exceptions.writeOff(user.actor(), reference, request.note(), context(http)));
    }

    // ------------------------------------------------------------------

    private static RequestContext context(HttpServletRequest request) {
        ClientInfo info = ClientInfo.from(request);
        return new RequestContext(info.ipAddress(), info.userAgent());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    record MappingRequest(@Size(max = 2) String delimiter, @Size(max = 40) String dateFormat,
                          @NotNull @Size(max = 10) Map<MappingProfile.Field, @Size(max = 100) String> columns) {
    }

    record FeeRuleRequest(@Size(max = 30) String method,
                          @Pattern(regexp = "^([A-Za-z]{3})?$") String currency,
                          @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal percentage,
                          @DecimalMin("0") @DecimalMax("100000") BigDecimal fixedFee) {
    }

    record FeeScheduleRequest(@NotNull @Size(max = 50) List<@Valid FeeRuleRequest> rules) {
    }

    record AssignRequest(@Size(max = 320) String assignee) {
    }

    record ResolveRequest(@NotNull ResolutionReason reason, @NotBlank @Size(max = 1000) String note) {
    }

    record MatchRequest(@NotBlank @Size(max = 100) String paymentReference, @Size(max = 1000) String note) {
    }

    record NoteRequest(@NotBlank @Size(max = 1000) String note) {
    }
}
