package com.payops.identity.adapter.in.web;

import com.payops.identity.adapter.config.SecurityProperties;
import com.payops.identity.application.port.in.AuthOutcome;
import com.payops.identity.application.port.in.AuthenticationUseCase;
import com.payops.identity.application.port.in.InvitationUseCase;
import com.payops.identity.application.port.in.MfaEnrollment;
import com.payops.identity.application.port.in.UserView;
import com.payops.shared.adapter.web.ApiResponse;
import com.payops.shared.adapter.web.ClientInfo;
import com.payops.shared.application.RequestContext;
import com.payops.shared.domain.UnauthorizedException;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Authentication endpoints. The refresh token never appears in a response
 * body: it travels only in an HttpOnly, SameSite=Strict cookie scoped to
 * this controller's path.
 */
@Tag(name = "Authentication")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
class AuthController {

    static final String REFRESH_COOKIE = "po_rt";
    static final String SESSION_HINT_COOKIE = "po_session";
    private static final String REFRESH_PATH = "/api/v1/auth";
    private static final String CSRF_HEADER_VALUE = "payops";

    private final AuthenticationUseCase authentication;
    private final InvitationUseCase invitations;
    private final SecurityProperties properties;

    @PostMapping("/login")
    ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http,
                                    HttpServletResponse response) {
        return ApiResponse.ok(toResponse(authentication.login(request.email(), request.password(), context(http)), response));
    }

    @PostMapping("/mfa/verify")
    ApiResponse<AuthResponse> verifyMfa(@Valid @RequestBody MfaRequest request, HttpServletRequest http,
                                        HttpServletResponse response) {
        return ApiResponse.ok(toResponse(authentication.completeMfa(request.challengeToken(), request.code(), context(http)), response));
    }

    @PostMapping("/mfa/enroll/start")
    ApiResponse<MfaEnrollment> startEnrollment(@Valid @RequestBody ChallengeRequest request) {
        return ApiResponse.ok(authentication.beginEnrollment(request.challengeToken()));
    }

    @PostMapping("/mfa/enroll/confirm")
    ApiResponse<AuthResponse> confirmEnrollment(@Valid @RequestBody MfaRequest request, HttpServletRequest http,
                                                HttpServletResponse response) {
        return ApiResponse.ok(toResponse(
                authentication.completeEnrollment(request.challengeToken(), request.code(), context(http)), response));
    }

    /**
     * Activates an invited account with the chosen password, then signs the
     * user in exactly like {@link #login} (MFA enrolment follows the
     * organisation's policy).
     */
    @PostMapping("/invitations/accept")
    ApiResponse<AuthResponse> acceptInvitation(@Valid @RequestBody AcceptInvitationRequest request,
                                               HttpServletRequest http, HttpServletResponse response) {
        RequestContext context = context(http);
        String email = invitations.accept(request.token(), request.password(), context);
        return ApiResponse.ok(toResponse(authentication.login(email, request.password(), context), response));
    }

    @PostMapping("/refresh")
    ApiResponse<AuthResponse> refresh(@CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
                                      @RequestHeader(name = "X-Requested-With", required = false) String requestedWith,
                                      HttpServletRequest http, HttpServletResponse response) {
        requireCsrfHeader(requestedWith);
        try {
            return ApiResponse.ok(toResponse(authentication.refresh(refreshToken, context(http)), response));
        } catch (UnauthorizedException ex) {
            clearCookies(response);
            throw ex;
        }
    }

    @PostMapping("/logout")
    ApiResponse<Void> logout(@CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
                             @RequestHeader(name = "X-Requested-With", required = false) String requestedWith,
                             HttpServletRequest http, HttpServletResponse response) {
        requireCsrfHeader(requestedWith);
        authentication.logout(refreshToken, context(http));
        clearCookies(response);
        return ApiResponse.ok(null);
    }

    // ------------------------------------------------------------------------

    private AuthResponse toResponse(AuthOutcome outcome, HttpServletResponse response) {
        if (outcome instanceof AuthOutcome.MfaRequired mfa) {
            return new AuthResponse("MFA_REQUIRED", null, null, null, null, mfa.challengeToken(),
                    mfa.enrollmentRequired(), List.of());
        }
        AuthOutcome.Authenticated auth = (AuthOutcome.Authenticated) outcome;
        writeCookies(response, auth.refreshToken(), auth.refreshTokenExpiresAt());
        return new AuthResponse("AUTHENTICATED", auth.accessToken(), auth.accessTokenExpiresAt(), auth.user(),
                auth.organizationName(), null, false, auth.recoveryCodes());
    }

    private void writeCookies(HttpServletResponse response, String refreshToken, Instant expiresAt) {
        Duration maxAge = Duration.between(Instant.now(), expiresAt);
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(REFRESH_COOKIE, refreshToken)
                .httpOnly(true).secure(properties.cookieSecure()).sameSite("Strict").path(REFRESH_PATH)
                .maxAge(maxAge).build().toString());
        // Non-sensitive hint so the web tier can redirect unauthenticated visitors early.
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(SESSION_HINT_COOKIE, "1")
                .httpOnly(true).secure(properties.cookieSecure()).sameSite("Strict").path("/")
                .maxAge(maxAge).build().toString());
    }

    private void clearCookies(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(REFRESH_COOKIE, "")
                .httpOnly(true).secure(properties.cookieSecure()).sameSite("Strict").path(REFRESH_PATH)
                .maxAge(0).build().toString());
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(SESSION_HINT_COOKIE, "")
                .httpOnly(true).secure(properties.cookieSecure()).sameSite("Strict").path("/")
                .maxAge(0).build().toString());
    }

    private static void requireCsrfHeader(String requestedWith) {
        if (!CSRF_HEADER_VALUE.equals(requestedWith)) {
            throw new UnauthorizedException("CSRF_CHECK_FAILED", "Missing anti-forgery header");
        }
    }

    private static RequestContext context(HttpServletRequest request) {
        ClientInfo info = ClientInfo.from(request);
        return new RequestContext(info.ipAddress(), info.userAgent());
    }

    // ------------------------------------------------------------------ DTOs

    record LoginRequest(@NotBlank @Email @Size(max = 320) String email,
                        @NotBlank @Size(max = 200) String password) {
    }

    record AcceptInvitationRequest(@NotBlank @Size(max = 128) String token,
                                   @NotBlank @Size(max = 200) String password) {
    }

    record ChallengeRequest(@NotBlank @Size(max = 2048) String challengeToken) {
    }

    record MfaRequest(@NotBlank @Size(max = 2048) String challengeToken,
                      @NotBlank @Size(max = 32) String code) {
    }

    record AuthResponse(String status, String accessToken, Instant accessTokenExpiresAt, UserView user,
                        String organizationName, String challengeToken, boolean mfaEnrollmentRequired,
                        List<String> recoveryCodes) {
    }
}
