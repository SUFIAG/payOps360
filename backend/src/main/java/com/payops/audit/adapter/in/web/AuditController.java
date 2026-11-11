package com.payops.audit.adapter.in.web;

import com.payops.audit.application.port.in.AuditQueryUseCase;
import com.payops.audit.domain.StoredAuditEntry;
import com.payops.shared.adapter.web.ApiResponse;
import com.payops.shared.adapter.web.AuthenticatedUser;
import com.payops.shared.adapter.web.PageResponse;
import com.payops.shared.domain.PageQuery;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@Tag(name = "Audit")
@RestController
@RequestMapping("/api/v1/audit")
@PreAuthorize("hasAuthority('AUDIT_READ')")
@RequiredArgsConstructor
class AuditController {

    private final AuditQueryUseCase audit;

    @GetMapping
    ApiResponse<PageResponse<AuditEntryResponse>> search(@AuthenticationPrincipal AuthenticatedUser user,
                                                         @RequestParam(required = false) String action,
                                                         @RequestParam(defaultValue = "0") int page,
                                                         @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.ok(PageResponse.from(
                audit.search(user.organizationId(), action, PageQuery.of(page, size)), AuditEntryResponse::of));
    }

    @GetMapping("/verify")
    ApiResponse<AuditQueryUseCase.ChainVerification> verify(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(audit.verifyChain(user.organizationId()));
    }

    record AuditEntryResponse(long id, Instant occurredAt, String actor, String action, String resourceType,
                              String resourceId, String outcome, String ipAddress, Map<String, String> details,
                              String hash) {

        static AuditEntryResponse of(StoredAuditEntry stored) {
            var e = stored.entry();
            return new AuditEntryResponse(stored.id(), e.occurredAt(), e.actorEmail(), e.action().name(),
                    e.resourceType(), e.resourceId(), e.outcome().name(), e.ipAddress(), e.details(),
                    stored.entryHash());
        }
    }
}
