package com.payops.identity.application.service;

import com.payops.audit.application.port.in.AuditTrail;
import com.payops.audit.domain.AuditAction;
import com.payops.audit.domain.AuditEntry;
import com.payops.audit.domain.AuditOutcome;
import com.payops.identity.application.port.in.PasswordRecoveryUseCase;
import com.payops.identity.application.port.out.PasswordHasher;
import com.payops.identity.application.port.out.PasswordHistoryRepository;
import com.payops.identity.application.port.out.SessionRepository;
import com.payops.identity.application.port.out.UserRepository;
import com.payops.identity.domain.ActionToken;
import com.payops.identity.domain.InvalidTokenException;
import com.payops.identity.domain.TokenPurpose;
import com.payops.identity.domain.User;
import com.payops.identity.domain.UserStatus;
import com.payops.shared.application.RequestContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;

/**
 * Forgot / reset password.
 *
 * <p>Security properties:
 * <ul>
 *   <li>No account enumeration: the request outcome is identical for unknown,
 *       disabled and active accounts (the web adapter also equalises timing).</li>
 *   <li>Single-use 256-bit token, stored hashed, 30 minute lifetime; a new
 *       request supersedes older links; at most one e-mail per minute per account.</li>
 *   <li>Completing a reset enforces the password policy and history, clears
 *       lockout, revokes every session and notifies the account owner.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PasswordRecoveryService implements PasswordRecoveryUseCase {

    static final Duration RESEND_COOLDOWN = Duration.ofMinutes(1);

    private final UserRepository users;
    private final SessionRepository sessions;
    private final PasswordHasher passwordHasher;
    private final PasswordHistoryRepository passwordHistory;
    private final PasswordRules passwordRules;
    private final OneTimeTokens tokens;
    private final IdentityEmails emails;
    private final AuditTrail auditTrail;
    private final Clock clock;

    @Override
    public void requestReset(String email, RequestContext context) {
        String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        User user = users.findByEmail(normalized).orElse(null);
        if (user == null || user.status() != UserStatus.ACTIVE) {
            audit(user, normalized, AuditAction.PASSWORD_RESET_REQUESTED, AuditOutcome.DENIED, context,
                    Map.of("reason", user == null ? "UNKNOWN_EMAIL" : "STATUS_" + user.status().name()));
            return;
        }
        if (tokens.issuedWithin(user.id(), TokenPurpose.PASSWORD_RESET, RESEND_COOLDOWN)) {
            audit(user, normalized, AuditAction.PASSWORD_RESET_REQUESTED, AuditOutcome.DENIED, context,
                    Map.of("reason", "THROTTLED"));
            return;
        }
        String token = tokens.issue(user.id(), TokenPurpose.PASSWORD_RESET);
        emails.passwordReset(user, token, context.ipAddress());
        audit(user, normalized, AuditAction.PASSWORD_RESET_REQUESTED, AuditOutcome.SUCCESS, context, Map.of());
    }

    @Override
    public void resetPassword(String rawToken, String newPassword, RequestContext context) {
        ActionToken token = tokens.resolve(rawToken, TokenPurpose.PASSWORD_RESET);
        User user = users.findById(token.userId()).orElseThrow(InvalidTokenException::new);
        if (user.status() != UserStatus.ACTIVE) {
            audit(user, user.email(), AuditAction.PASSWORD_RESET_COMPLETED, AuditOutcome.DENIED, context,
                    Map.of("reason", "STATUS_" + user.status().name()));
            throw new InvalidTokenException();
        }
        // Validate before consuming so a rejected password does not burn the link.
        passwordRules.enforceNewPassword(user, newPassword);
        tokens.consume(token);

        Instant now = clock.instant();
        passwordHistory.add(user.id(), user.passwordHash());
        user.completePasswordReset(passwordHasher.hash(newPassword), now);
        users.save(user);
        int revoked = sessions.revokeAllForUser(user.id(), "PASSWORD_RESET", now);
        audit(user, user.email(), AuditAction.PASSWORD_RESET_COMPLETED, AuditOutcome.SUCCESS, context,
                Map.of("revokedSessions", String.valueOf(revoked)));
        emails.passwordChanged(user, now, context.ipAddress());
    }

    private void audit(User user, String email, AuditAction action, AuditOutcome outcome, RequestContext context,
                       Map<String, String> details) {
        auditTrail.record(new AuditEntry(user == null ? null : user.organizationId(), user == null ? null : user.id(),
                email, action, "USER", user == null ? null : user.publicId().toString(), outcome,
                context.ipAddress(), context.userAgent(), details, clock.instant()));
    }
}
