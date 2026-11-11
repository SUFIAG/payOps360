package com.payops.notification.adapter.input.web;

import com.payops.notification.application.port.input.NotificationAdminUseCase;
import com.payops.notification.application.port.input.NotificationAdminUseCase.ChannelView;
import com.payops.notification.application.port.input.NotificationAdminUseCase.DeliveryView;
import com.payops.notification.application.port.input.NotificationAdminUseCase.PolicyView;
import com.payops.notification.application.port.input.NotificationAdminUseCase.RuleView;
import com.payops.notification.application.port.input.NotificationAdminUseCase.TestResult;
import com.payops.notification.domain.ChannelType;
import com.payops.notification.domain.NotificationDelivery;
import com.payops.shared.adapter.web.ApiResponse;
import com.payops.shared.adapter.web.AuthenticatedUser;
import com.payops.shared.adapter.web.ClientInfo;
import com.payops.shared.application.OperationalEvent;
import com.payops.shared.application.RequestContext;
import com.payops.shared.domain.Severity;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Notification channels, routing rules, escalation policies and the delivery
 * log. Secrets are write-only: responses only carry masked targets.
 */
@Tag(name = "Notifications")
@RestController
@RequestMapping("/api/v1/notifications")
@PreAuthorize("hasAuthority('NOTIFICATION_MANAGE')")
@RequiredArgsConstructor
class NotificationController {

    private final NotificationAdminUseCase admin;

    // ------------------------------------------------------------ channels

    @GetMapping("/channels")
    ApiResponse<List<ChannelView>> channels(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(admin.channels(user.organizationId()));
    }

    @PostMapping("/channels")
    ApiResponse<ChannelView> createChannel(@AuthenticationPrincipal AuthenticatedUser user,
                                           @Valid @RequestBody ChannelRequest request, HttpServletRequest http) {
        return ApiResponse.ok(admin.createChannel(user.actor(), request.toCommand(), context(http)));
    }

    @PutMapping("/channels/{id}")
    ApiResponse<ChannelView> updateChannel(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
                                           @Valid @RequestBody ChannelRequest request, HttpServletRequest http) {
        return ApiResponse.ok(admin.updateChannel(user.actor(), id, request.toCommand(), context(http)));
    }

    @DeleteMapping("/channels/{id}")
    ApiResponse<Void> deleteChannel(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
                                    HttpServletRequest http) {
        admin.deleteChannel(user.actor(), id, context(http));
        return ApiResponse.ok(null);
    }

    @PostMapping("/channels/{id}/test")
    ApiResponse<TestResult> testChannel(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
                                        HttpServletRequest http) {
        return ApiResponse.ok(admin.testChannel(user.actor(), id, context(http)));
    }

    // --------------------------------------------------------------- rules

    @GetMapping("/rules")
    ApiResponse<List<RuleView>> rules(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(admin.rules(user.organizationId()));
    }

    @PostMapping("/rules")
    ApiResponse<RuleView> createRule(@AuthenticationPrincipal AuthenticatedUser user,
                                     @Valid @RequestBody RuleRequest request, HttpServletRequest http) {
        return ApiResponse.ok(admin.createRule(user.actor(), request.toCommand(), context(http)));
    }

    @PutMapping("/rules/{id}")
    ApiResponse<RuleView> updateRule(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
                                     @Valid @RequestBody RuleRequest request, HttpServletRequest http) {
        return ApiResponse.ok(admin.updateRule(user.actor(), id, request.toCommand(), context(http)));
    }

    @DeleteMapping("/rules/{id}")
    ApiResponse<Void> deleteRule(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
                                 HttpServletRequest http) {
        admin.deleteRule(user.actor(), id, context(http));
        return ApiResponse.ok(null);
    }

    // ---------------------------------------------------------- escalation

    @GetMapping("/escalation-policies")
    ApiResponse<List<PolicyView>> policies(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(admin.escalationPolicies(user.organizationId()));
    }

    @PostMapping("/escalation-policies")
    ApiResponse<PolicyView> createPolicy(@AuthenticationPrincipal AuthenticatedUser user,
                                         @Valid @RequestBody PolicyRequest request, HttpServletRequest http) {
        return ApiResponse.ok(admin.createPolicy(user.actor(), request.toCommand(), context(http)));
    }

    @PutMapping("/escalation-policies/{id}")
    ApiResponse<PolicyView> updatePolicy(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
                                         @Valid @RequestBody PolicyRequest request, HttpServletRequest http) {
        return ApiResponse.ok(admin.updatePolicy(user.actor(), id, request.toCommand(), context(http)));
    }

    @DeleteMapping("/escalation-policies/{id}")
    ApiResponse<Void> deletePolicy(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
                                   HttpServletRequest http) {
        admin.deletePolicy(user.actor(), id, context(http));
        return ApiResponse.ok(null);
    }

    // ---------------------------------------------------------- deliveries

    @GetMapping("/deliveries")
    ApiResponse<List<DeliveryView>> deliveries(@AuthenticationPrincipal AuthenticatedUser user,
                                               @RequestParam(required = false) NotificationDelivery.Status status,
                                               @RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.ok(admin.deliveries(user.organizationId(), status, limit));
    }

    // ------------------------------------------------------------------------

    private static RequestContext context(HttpServletRequest request) {
        ClientInfo info = ClientInfo.from(request);
        return new RequestContext(info.ipAddress(), info.userAgent());
    }

    /**
     * @param target        URL, routing key or comma-separated addresses; blank on update keeps the stored value
     * @param signingSecret WEBHOOK only; blank on update keeps the stored secret
     */
    record ChannelRequest(@NotBlank @Size(max = 120) String name, ChannelType type, Boolean enabled,
                          @Size(max = 2000) String target, @Size(max = 200) String signingSecret) {

        NotificationAdminUseCase.ChannelCommand toCommand() {
            return new NotificationAdminUseCase.ChannelCommand(name, type, enabled == null || enabled, target,
                    signingSecret);
        }
    }

    record RuleRequest(@NotBlank @Size(max = 120) String name, @NotNull OperationalEvent.Type eventType,
                       @NotNull Severity minSeverity, @Size(max = 60) String providerCode,
                       @NotNull @Size(min = 1, max = 20) List<@NotNull UUID> channelIds, Boolean enabled) {

        NotificationAdminUseCase.RuleCommand toCommand() {
            return new NotificationAdminUseCase.RuleCommand(name, eventType, minSeverity, providerCode, channelIds,
                    enabled == null || enabled);
        }
    }

    record LevelRequest(@Min(0) @Max(1440) int delayMinutes,
                        @NotNull @Size(min = 1, max = 20) List<@NotNull UUID> channelIds) {
    }

    record PolicyRequest(@NotBlank @Size(max = 120) String name, @NotNull Severity minSeverity, Boolean enabled,
                         @NotNull @Size(min = 1, max = 10) List<@Valid @NotNull LevelRequest> levels) {

        NotificationAdminUseCase.PolicyCommand toCommand() {
            return new NotificationAdminUseCase.PolicyCommand(name, minSeverity, enabled == null || enabled,
                    levels.stream().map(l -> new NotificationAdminUseCase.LevelCommand(l.delayMinutes(), l.channelIds()))
                            .toList());
        }
    }
}
