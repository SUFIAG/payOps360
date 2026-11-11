package com.payops.alert.adapter.in.web;

import com.payops.alert.application.port.in.AlertCommandUseCase;
import com.payops.alert.application.port.in.AlertQueryUseCase;
import com.payops.alert.application.port.in.AlertView;
import com.payops.alert.domain.AlertStatus;
import com.payops.shared.adapter.web.ApiResponse;
import com.payops.shared.adapter.web.AuthenticatedUser;
import com.payops.shared.adapter.web.ClientInfo;
import com.payops.shared.adapter.web.PageResponse;
import com.payops.shared.application.RequestContext;
import com.payops.shared.domain.PageQuery;
import com.payops.shared.domain.Severity;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Alerts")
@RestController
@RequestMapping("/api/v1/alerts")
@RequiredArgsConstructor
class AlertController {

    private final AlertQueryUseCase queries;
    private final AlertCommandUseCase commands;

    @GetMapping
    @PreAuthorize("hasAuthority('ALERT_READ')")
    ApiResponse<PageResponse<AlertView>> list(@AuthenticationPrincipal AuthenticatedUser user,
                                              @RequestParam(required = false) AlertStatus status,
                                              @RequestParam(required = false) Severity severity,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "25") int size) {
        return ApiResponse.ok(PageResponse.from(
                queries.list(user.organizationId(), status, severity, PageQuery.of(page, size)), v -> v));
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('ALERT_READ')")
    ApiResponse<AlertQueryUseCase.Summary> summary(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(queries.summary(user.organizationId()));
    }

    @GetMapping("/{reference}")
    @PreAuthorize("hasAuthority('ALERT_READ')")
    ApiResponse<AlertView> get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String reference) {
        return ApiResponse.ok(queries.get(user.organizationId(), reference));
    }

    @PostMapping("/{reference}/acknowledge")
    @PreAuthorize("hasAuthority('ALERT_MANAGE')")
    ApiResponse<AlertView> acknowledge(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String reference,
                                       HttpServletRequest http) {
        return ApiResponse.ok(commands.acknowledge(user.actor(), reference, context(http)));
    }

    @PostMapping("/{reference}/resolve")
    @PreAuthorize("hasAuthority('ALERT_MANAGE')")
    ApiResponse<AlertView> resolve(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String reference,
                                   @Valid @RequestBody ResolveRequest request, HttpServletRequest http) {
        return ApiResponse.ok(commands.resolve(user.actor(), reference, request.note(), context(http)));
    }

    private static RequestContext context(HttpServletRequest request) {
        ClientInfo info = ClientInfo.from(request);
        return new RequestContext(info.ipAddress(), info.userAgent());
    }

    record ResolveRequest(@NotBlank @Size(max = 2000) String note) {
    }
}
