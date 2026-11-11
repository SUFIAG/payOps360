package com.payops.identity.application.service;

import com.payops.audit.application.port.input.AuditTrail;
import com.payops.audit.domain.AuditAction;
import com.payops.audit.domain.AuditEntry;
import com.payops.audit.domain.AuditOutcome;
import com.payops.identity.application.port.input.AuthOutcome;
import com.payops.identity.application.port.input.AuthenticationUseCase;
import com.payops.identity.application.port.input.MfaEnrollment;
import com.payops.identity.application.port.input.SessionValidationUseCase;
import com.payops.identity.application.port.output.LoginAttemptRepository;
import com.payops.identity.application.port.output.OrganizationRepository;
import com.payops.identity.application.port.output.PasswordHasher;
import com.payops.identity.application.port.output.SecretCipher;
import com.payops.identity.application.port.output.SessionRepository;
import com.payops.identity.application.port.output.TokenService;
import com.payops.identity.application.port.output.UserRepository;
import com.payops.identity.domain.AuthenticationFailedException;
import com.payops.identity.domain.Organization;
import com.payops.identity.domain.Totp;
import com.payops.identity.domain.User;
import com.payops.identity.domain.UserSession;
import com.payops.identity.domain.UserStatus;
import com.payops.shared.application.RequestContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;

/**
 * Login, MFA challenge/enrolment, refresh-token rotation and logout.
 *
 * <p>Security properties:
 * <ul>
 *   <li>Identical error for unknown user, wrong password, locked or disabled
 *       account, with equalised hashing cost (no user enumeration).</li>
 *   <li>Progressive account lockout; MFA failures count towards it.</li>
 *   <li>Per-IP failure ceiling against credential stuffing.</li>
 *   <li>Refresh tokens rotate on every use; reuse revokes the whole family.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(noRollbackFor = AuthenticationFailedException.class)
public class AuthenticationService implements AuthenticationUseCase, SessionValidationUseCase {

    private static final Duration IP_WINDOW = Duration.ofMinutes(15);
    /** Concurrent refreshes from two tabs within this window are not treated as theft. */
    private static final Duration ROTATION_GRACE = Duration.ofSeconds(10);

    private final UserRepository users;
    private final OrganizationRepository organizations;
    private final SessionRepository sessions;
    private final LoginAttemptRepository loginAttempts;
    private final PasswordHasher passwordHasher;
    private final TokenService tokens;
    private final SecretCipher cipher;
    private final SessionIssuer sessionIssuer;
    private final SecondFactorVerifier secondFactor;
    private final RecoveryCodes recoveryCodes;
    private final AuditTrail auditTrail;
    private final IdentitySettings settings;
    private final Clock clock;

    @Override
    public AuthOutcome login(String email, String password, RequestContext context) {
        Instant now = clock.instant();
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase();

        if (loginAttempts.countFailuresFromIp(context.ipAddress(), now.minus(IP_WINDOW)) >= settings.maxIpFailuresPer15Minutes()) {
            audit(null, null, normalizedEmail, AuditAction.LOGIN_BLOCKED, AuditOutcome.DENIED, context, Map.of("reason", "IP_THROTTLED"));
            throw new AuthenticationFailedException("TOO_MANY_ATTEMPTS",
                    "Too many failed attempts from your network. Try again later.");
        }

        User user = users.findByEmail(normalizedEmail).orElse(null);
        if (user == null) {
            passwordHasher.simulateVerification();
            throw fail(normalizedEmail, null, context, "UNKNOWN_USER");
        }
        if (user.status() == UserStatus.PENDING_VERIFICATION && !user.isLocked(now)) {
            // Only someone who knows the password learns that verification is pending.
            if (!passwordHasher.matches(password == null ? "" : password, user.passwordHash())) {
                throw registerFailure(user, context, "BAD_PASSWORD");
            }
            loginAttempts.record(normalizedEmail, context.ipAddress(), context.userAgent(), false,
                    "EMAIL_NOT_VERIFIED", now);
            audit(user.organizationId(), user.id(), normalizedEmail, AuditAction.LOGIN_FAILED, AuditOutcome.DENIED,
                    context, Map.of("reason", "EMAIL_NOT_VERIFIED"));
            throw new AuthenticationFailedException("EMAIL_NOT_VERIFIED",
                    "Please verify your email address first. Check your inbox for the verification link.");
        }
        if (!user.canAuthenticate(now)) {
            passwordHasher.simulateVerification();
            throw fail(normalizedEmail, user, context, user.isLocked(now) ? "LOCKED" : user.status().name());
        }
        if (!passwordHasher.matches(password == null ? "" : password, user.passwordHash())) {
            throw registerFailure(user, context, "BAD_PASSWORD");
        }

        Organization organization = organizations.findById(user.organizationId())
                .orElseThrow(AuthenticationFailedException::new);
        if (user.mfaEnabled() || organization.mfaRequired()) {
            TokenService.IssuedToken challenge = tokens.issueMfaChallengeToken(user);
            return new AuthOutcome.MfaRequired(challenge.value(), challenge.expiresAt(), !user.mfaEnabled());
        }
        return sessionIssuer.issue(user, context, List.of(), "PASSWORD");
    }

    @Override
    public AuthOutcome completeMfa(String challengeToken, String code, RequestContext context) {
        User user = challengedUser(challengeToken);
        SecondFactorVerifier.Result result = secondFactor.verify(user, code);
        if (result == SecondFactorVerifier.Result.REJECTED) {
            audit(user.organizationId(), user.id(), user.email(), AuditAction.MFA_CHALLENGE_FAILED,
                    AuditOutcome.FAILURE, context, Map.of());
            throw registerFailure(user, context, "BAD_MFA_CODE");
        }
        if (result == SecondFactorVerifier.Result.RECOVERY_CODE) {
            audit(user.organizationId(), user.id(), user.email(), AuditAction.MFA_RECOVERY_CODE_USED,
                    AuditOutcome.SUCCESS, context, Map.of());
        }
        return sessionIssuer.issue(user, context, List.of(), "PASSWORD+" + result.name());
    }

    @Override
    public MfaEnrollment beginEnrollment(String challengeToken) {
        User user = challengedUser(challengeToken);
        if (user.mfaEnabled()) {
            throw new AuthenticationFailedException("MFA_ALREADY_ENABLED", "MFA is already configured");
        }
        String secret = Totp.generateSecret();
        user.stageMfaSecret(cipher.encrypt(secret));
        users.save(user);
        return new MfaEnrollment(secret, Totp.otpauthUri(settings.mfaIssuer(), user.email(), secret));
    }

    @Override
    public AuthOutcome completeEnrollment(String challengeToken, String code, RequestContext context) {
        User user = challengedUser(challengeToken);
        if (user.mfaEnabled()) {
            throw new AuthenticationFailedException("MFA_ALREADY_ENABLED", "MFA is already configured");
        }
        OptionalLong step = secondFactor.verifyStaged(user, code);
        if (step.isEmpty()) {
            throw registerFailure(user, context, "BAD_MFA_ENROLMENT_CODE");
        }
        user.activateMfa(step.getAsLong());
        List<String> codes = recoveryCodes.regenerate(user.id());
        audit(user.organizationId(), user.id(), user.email(), AuditAction.MFA_ENABLED, AuditOutcome.SUCCESS, context, Map.of());
        return sessionIssuer.issue(user, context, codes, "PASSWORD+TOTP_ENROLMENT");
    }

    @Override
    public AuthOutcome refresh(String refreshToken, RequestContext context) {
        Instant now = clock.instant();
        if (refreshToken == null || refreshToken.isBlank()) {
            throw sessionInvalid();
        }
        UserSession session = sessions.findByRefreshTokenHash(tokens.hashOpaqueToken(refreshToken))
                .orElseThrow(this::sessionInvalid);

        if (session.revokedAt() != null) {
            boolean benignRace = "ROTATED".equals(session.revokedReason())
                    && session.revokedAt().plus(ROTATION_GRACE).isAfter(now);
            if ("ROTATED".equals(session.revokedReason()) && !benignRace) {
                int revoked = sessions.revokeFamily(session.familyId(), "TOKEN_REUSE", now);
                users.findById(session.userId()).ifPresent(u -> audit(u.organizationId(), u.id(), u.email(),
                        AuditAction.TOKEN_REUSE_DETECTED, AuditOutcome.DENIED, context,
                        Map.of("revokedSessions", String.valueOf(revoked))));
                log.warn("Refresh token reuse detected for user={} family={}", session.userId(), session.familyId());
            }
            throw sessionInvalid();
        }
        if (!session.isActive(now)) {
            throw sessionInvalid();
        }
        User user = users.findById(session.userId()).orElseThrow(this::sessionInvalid);
        if (!user.canAuthenticate(now)) {
            session.revoke("USER_INACTIVE", now);
            sessions.save(session);
            throw sessionInvalid();
        }
        return sessionIssuer.rotate(user, session, context);
    }

    @Override
    public void logout(String refreshToken, RequestContext context) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        Instant now = clock.instant();
        sessions.findByRefreshTokenHash(tokens.hashOpaqueToken(refreshToken)).ifPresent(session -> {
            session.revoke("LOGOUT", now);
            sessions.save(session);
            users.findById(session.userId()).ifPresent(u -> audit(u.organizationId(), u.id(), u.email(),
                    AuditAction.LOGOUT, AuditOutcome.SUCCESS, context, Map.of()));
        });
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isSessionActive(UUID sessionId) {
        return sessions.isActive(sessionId, clock.instant());
    }

    // ------------------------------------------------------------------------

    private User challengedUser(String challengeToken) {
        Long userId = tokens.verifyMfaChallengeToken(challengeToken)
                .orElseThrow(() -> new AuthenticationFailedException("MFA_CHALLENGE_EXPIRED",
                        "Your sign-in attempt expired. Please sign in again."));
        User user = users.findById(userId).orElseThrow(AuthenticationFailedException::new);
        if (!user.canAuthenticate(clock.instant())) {
            throw new AuthenticationFailedException();
        }
        return user;
    }

    private AuthenticationFailedException registerFailure(User user, RequestContext context, String reason) {
        Instant now = clock.instant();
        boolean locked = user.registerFailedLogin(settings.lockoutPolicy(), now);
        users.save(user);
        if (locked) {
            audit(user.organizationId(), user.id(), user.email(), AuditAction.ACCOUNT_LOCKED, AuditOutcome.DENIED,
                    context, Map.of("lockedUntil", user.lockedUntil().toString(),
                            "failedAttempts", String.valueOf(user.failedLoginAttempts())));
        }
        return fail(user.email(), user, context, reason);
    }

    private AuthenticationFailedException fail(String email, User user, RequestContext context, String reason) {
        loginAttempts.record(email, context.ipAddress(), context.userAgent(), false, reason, clock.instant());
        audit(user == null ? null : user.organizationId(), user == null ? null : user.id(), email,
                AuditAction.LOGIN_FAILED, AuditOutcome.FAILURE, context, Map.of("reason", reason));
        return new AuthenticationFailedException();
    }

    private AuthenticationFailedException sessionInvalid() {
        return new AuthenticationFailedException("SESSION_INVALID", "Your session has ended. Please sign in again.");
    }

    private void audit(Long orgId, Long userId, String email, AuditAction action, AuditOutcome outcome,
                       RequestContext context, Map<String, String> details) {
        auditTrail.record(new AuditEntry(orgId, userId, email, action, "USER",
                userId == null ? null : String.valueOf(userId), outcome, context.ipAddress(), context.userAgent(),
                details, clock.instant()));
    }
}
