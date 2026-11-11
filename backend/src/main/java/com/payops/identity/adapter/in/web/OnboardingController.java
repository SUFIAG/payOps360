package com.payops.identity.adapter.in.web;

import com.payops.identity.application.port.in.InvitationUseCase;
import com.payops.identity.application.port.in.PasswordRecoveryUseCase;
import com.payops.identity.application.port.in.SignupUseCase;
import com.payops.shared.adapter.web.ApiResponse;
import com.payops.shared.adapter.web.ClientInfo;
import com.payops.shared.application.RequestContext;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Public onboarding endpoints: password recovery, invitation lookup,
 * organisation sign-up and e-mail verification. Endpoints that reveal nothing
 * about account existence answer 202 with a fixed message after a minimum
 * response time, so neither the body nor the latency leaks whether an e-mail
 * address is registered. (Invitation acceptance issues a session and lives in
 * {@link AuthController}.)
 */
@Tag(name = "Onboarding")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
class OnboardingController {

    /** Covers the slowest path (BCrypt hashing plus outbox writes) with margin. */
    private static final Duration RESPONSE_FLOOR = Duration.ofMillis(700);
    private static final String RESET_ACCEPTED =
            "If an account exists for this address, a password reset link is on its way.";
    private static final String SIGNUP_ACCEPTED =
            "Check your inbox: we sent a link to verify your e-mail address and activate the workspace.";
    private static final String VERIFICATION_ACCEPTED =
            "If this address is awaiting verification, a new link is on its way.";

    private final PasswordRecoveryUseCase passwordRecovery;
    private final InvitationUseCase invitations;
    private final SignupUseCase signup;

    @GetMapping("/config")
    ApiResponse<SignupUseCase.OnboardingConfig> config() {
        return ApiResponse.ok(signup.config());
    }

    @PostMapping("/password/forgot")
    ResponseEntity<ApiResponse<Accepted>> forgotPassword(@Valid @RequestBody EmailRequest request,
                                                         HttpServletRequest http) {
        withResponseFloor(() -> passwordRecovery.requestReset(request.email(), context(http)));
        return accepted(RESET_ACCEPTED);
    }

    @PostMapping("/password/reset")
    ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request, HttpServletRequest http) {
        passwordRecovery.resetPassword(request.token(), request.newPassword(), context(http));
        return ApiResponse.ok(null);
    }

    @GetMapping("/invitations/{token}")
    ApiResponse<InvitationUseCase.InvitationDetails> invitation(@PathVariable String token) {
        return ApiResponse.ok(invitations.describe(token));
    }

    @PostMapping("/signup")
    ResponseEntity<ApiResponse<Accepted>> signup(@Valid @RequestBody SignupRequest request, HttpServletRequest http) {
        withResponseFloor(() -> signup.signup(new SignupUseCase.SignupCommand(request.organizationName(),
                request.fullName(), request.email(), request.password(), request.baseCurrency()), context(http)));
        return accepted(SIGNUP_ACCEPTED);
    }

    @PostMapping("/verify-email")
    ApiResponse<Void> verifyEmail(@Valid @RequestBody TokenRequest request, HttpServletRequest http) {
        signup.verifyEmail(request.token(), context(http));
        return ApiResponse.ok(null);
    }

    @PostMapping("/verify-email/resend")
    ResponseEntity<ApiResponse<Accepted>> resendVerification(@Valid @RequestBody EmailRequest request,
                                                             HttpServletRequest http) {
        withResponseFloor(() -> signup.resendVerification(request.email(), context(http)));
        return accepted(VERIFICATION_ACCEPTED);
    }

    // ------------------------------------------------------------------------

    private static ResponseEntity<ApiResponse<Accepted>> accepted(String message) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.ok(new Accepted(message)));
    }

    /** Pads the call to a fixed minimum duration (requests run on virtual threads, so sleeping is cheap). */
    private static void withResponseFloor(Runnable action) {
        long deadline = System.nanoTime() + RESPONSE_FLOOR.toNanos();
        try {
            action.run();
        } finally {
            long remaining = deadline - System.nanoTime();
            if (remaining > 0) {
                try {
                    TimeUnit.NANOSECONDS.sleep(remaining);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private static RequestContext context(HttpServletRequest request) {
        ClientInfo info = ClientInfo.from(request);
        return new RequestContext(info.ipAddress(), info.userAgent());
    }

    // ------------------------------------------------------------------ DTOs

    record Accepted(String message) {
    }

    record EmailRequest(@NotBlank @Email @Size(max = 320) String email) {
    }

    record TokenRequest(@NotBlank @Size(max = 128) String token) {
    }

    record ResetPasswordRequest(@NotBlank @Size(max = 128) String token,
                                @NotBlank @Size(max = 200) String newPassword) {
    }

    record SignupRequest(@NotBlank @Size(min = 2, max = 200) String organizationName,
                         @NotBlank @Size(min = 2, max = 200) String fullName,
                         @NotBlank @Email @Size(max = 320) String email,
                         @NotBlank @Size(max = 200) String password,
                         @NotBlank @Pattern(regexp = "[A-Za-z]{3}", message = "must be a 3-letter ISO currency code")
                         String baseCurrency) {
    }
}
