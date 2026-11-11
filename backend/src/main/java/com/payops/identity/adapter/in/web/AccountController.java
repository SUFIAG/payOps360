package com.payops.identity.adapter.in.web;

import com.payops.identity.application.port.in.AccountUseCase;
import com.payops.identity.application.port.in.MfaEnrollment;
import com.payops.identity.application.port.in.SessionView;
import com.payops.identity.application.port.in.UserView;
import com.payops.shared.adapter.web.ApiResponse;
import com.payops.shared.adapter.web.AuthenticatedUser;
import com.payops.shared.adapter.web.ClientInfo;
import com.payops.shared.application.RequestContext;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Self-service account security: profile, password, MFA and sessions.
 */
@Tag(name = "Account")
@RestController
@RequestMapping("/api/v1/account")
@RequiredArgsConstructor
class AccountController {

    private final AccountUseCase account;

    @GetMapping("/me")
    ApiResponse<MeResponse> me(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(new MeResponse(account.me(user.actor()), account.organizationName(user.actor())));
    }

    @PostMapping("/password")
    ApiResponse<Void> changePassword(@AuthenticationPrincipal AuthenticatedUser user,
                                     @Valid @RequestBody ChangePasswordRequest request, HttpServletRequest http) {
        account.changePassword(user.actor(), request.currentPassword(), request.newPassword(), user.sessionId(), context(http));
        return ApiResponse.ok(null);
    }

    @PostMapping("/mfa/enroll/start")
    ApiResponse<MfaEnrollment> startMfa(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(account.beginMfaEnrollment(user.actor()));
    }

    @PostMapping("/mfa/enroll/confirm")
    ApiResponse<RecoveryCodesResponse> confirmMfa(@AuthenticationPrincipal AuthenticatedUser user,
                                                  @Valid @RequestBody CodeRequest request, HttpServletRequest http) {
        return ApiResponse.ok(new RecoveryCodesResponse(account.confirmMfaEnrollment(user.actor(), request.code(), context(http))));
    }

    @PostMapping("/mfa/disable")
    ApiResponse<Void> disableMfa(@AuthenticationPrincipal AuthenticatedUser user,
                                 @Valid @RequestBody DisableMfaRequest request, HttpServletRequest http) {
        account.disableMfa(user.actor(), request.password(), request.code(), context(http));
        return ApiResponse.ok(null);
    }

    @PostMapping("/mfa/recovery-codes")
    ApiResponse<RecoveryCodesResponse> regenerateRecoveryCodes(@AuthenticationPrincipal AuthenticatedUser user,
                                                               @Valid @RequestBody CodeRequest request,
                                                               HttpServletRequest http) {
        return ApiResponse.ok(new RecoveryCodesResponse(
                account.regenerateRecoveryCodes(user.actor(), request.code(), context(http))));
    }

    @GetMapping("/sessions")
    ApiResponse<List<SessionView>> sessions(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(account.sessions(user.actor(), user.sessionId()));
    }

    @DeleteMapping("/sessions/{sessionId}")
    ApiResponse<Void> revokeSession(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID sessionId,
                                    HttpServletRequest http) {
        account.revokeSession(user.actor(), sessionId, context(http));
        return ApiResponse.ok(null);
    }

    private static RequestContext context(HttpServletRequest request) {
        ClientInfo info = ClientInfo.from(request);
        return new RequestContext(info.ipAddress(), info.userAgent());
    }

    record MeResponse(UserView user, String organizationName) {
    }

    record ChangePasswordRequest(@NotBlank @Size(max = 200) String currentPassword,
                                 @NotBlank @Size(max = 200) String newPassword) {
    }

    record CodeRequest(@NotBlank @Size(max = 32) String code) {
    }

    record DisableMfaRequest(@NotBlank @Size(max = 200) String password, @NotBlank @Size(max = 32) String code) {
    }

    record RecoveryCodesResponse(List<String> recoveryCodes) {
    }
}
