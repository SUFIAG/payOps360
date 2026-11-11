package com.payops.identity.application.service;

import com.payops.audit.application.port.input.AuditTrail;
import com.payops.audit.domain.AuditAction;
import com.payops.audit.domain.AuditEntry;
import com.payops.audit.domain.AuditOutcome;
import com.payops.identity.application.port.input.AccountUseCase;
import com.payops.identity.application.port.input.MfaEnrollment;
import com.payops.identity.application.port.input.SessionView;
import com.payops.identity.application.port.input.UserView;
import com.payops.identity.application.port.output.OrganizationRepository;
import com.payops.identity.application.port.output.PasswordHasher;
import com.payops.identity.application.port.output.PasswordHistoryRepository;
import com.payops.identity.application.port.output.SecretCipher;
import com.payops.identity.application.port.output.SessionRepository;
import com.payops.identity.application.port.output.UserRepository;
import com.payops.identity.domain.AuthenticationFailedException;
import com.payops.identity.domain.Organization;
import com.payops.identity.domain.Totp;
import com.payops.identity.domain.User;
import com.payops.identity.domain.UserSession;
import com.payops.shared.application.Actor;
import com.payops.shared.application.RequestContext;
import com.payops.shared.domain.DomainException;
import com.payops.shared.domain.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class AccountService implements AccountUseCase {

    private final UserRepository users;
    private final OrganizationRepository organizations;
    private final SessionRepository sessions;
    private final PasswordHasher passwordHasher;
    private final PasswordHistoryRepository passwordHistory;
    private final PasswordRules passwordRules;
    private final SecretCipher cipher;
    private final SecondFactorVerifier secondFactor;
    private final RecoveryCodes recoveryCodes;
    private final AuditTrail auditTrail;
    private final IdentitySettings settings;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public UserView me(Actor actor) {
        return UserView.of(load(actor), clock.instant());
    }

    @Override
    @Transactional(readOnly = true)
    public String organizationName(Actor actor) {
        return organizations.findById(actor.organizationId()).map(Organization::name).orElse("");
    }

    @Override
    public void changePassword(Actor actor, String currentPassword, String newPassword, UUID currentSessionId,
                               RequestContext context) {
        User user = load(actor);
        if (!passwordHasher.matches(currentPassword, user.passwordHash())) {
            throw new AuthenticationFailedException("INVALID_CURRENT_PASSWORD", "Current password is incorrect");
        }
        passwordRules.enforceNewPassword(user, newPassword);
        Instant now = clock.instant();
        passwordHistory.add(user.id(), user.passwordHash());
        user.changePassword(passwordHasher.hash(newPassword), now);
        users.save(user);
        // Sign out every other device after a credential change.
        sessions.findActiveByUser(user.id(), now).stream()
                .filter(s -> !s.publicId().equals(currentSessionId))
                .forEach(s -> {
                    s.revoke("PASSWORD_CHANGED", now);
                    sessions.save(s);
                });
        audit(user, AuditAction.PASSWORD_CHANGED, context, Map.of());
    }

    @Override
    public MfaEnrollment beginMfaEnrollment(Actor actor) {
        User user = load(actor);
        if (user.mfaEnabled()) {
            throw new DomainException("MFA_ALREADY_ENABLED", "MFA is already enabled");
        }
        String secret = Totp.generateSecret();
        user.stageMfaSecret(cipher.encrypt(secret));
        users.save(user);
        return new MfaEnrollment(secret, Totp.otpauthUri(settings.mfaIssuer(), user.email(), secret));
    }

    @Override
    public List<String> confirmMfaEnrollment(Actor actor, String code, RequestContext context) {
        User user = load(actor);
        if (user.mfaEnabled()) {
            throw new DomainException("MFA_ALREADY_ENABLED", "MFA is already enabled");
        }
        OptionalLong step = secondFactor.verifyStaged(user, code);
        if (step.isEmpty()) {
            throw new DomainException("INVALID_MFA_CODE", "The verification code is invalid or expired");
        }
        user.activateMfa(step.getAsLong());
        users.save(user);
        audit(user, AuditAction.MFA_ENABLED, context, Map.of());
        return recoveryCodes.regenerate(user.id());
    }

    @Override
    public void disableMfa(Actor actor, String password, String code, RequestContext context) {
        User user = load(actor);
        boolean orgRequiresMfa = organizations.findById(user.organizationId()).map(Organization::mfaRequired).orElse(true);
        if (orgRequiresMfa) {
            throw new DomainException("MFA_MANDATORY", "Your organisation requires MFA; it cannot be disabled");
        }
        if (!passwordHasher.matches(password, user.passwordHash())
                || secondFactor.verify(user, code) == SecondFactorVerifier.Result.REJECTED) {
            throw new AuthenticationFailedException("INVALID_CREDENTIALS", "Password or verification code is incorrect");
        }
        user.disableMfa();
        users.save(user);
        recoveryCodes.deleteAll(user.id());
        audit(user, AuditAction.MFA_DISABLED, context, Map.of());
    }

    @Override
    public List<String> regenerateRecoveryCodes(Actor actor, String code, RequestContext context) {
        User user = load(actor);
        if (secondFactor.verify(user, code) != SecondFactorVerifier.Result.TOTP) {
            throw new DomainException("INVALID_MFA_CODE", "The verification code is invalid or expired");
        }
        users.save(user);
        return recoveryCodes.regenerate(user.id());
    }

    @Override
    @Transactional(readOnly = true)
    public List<SessionView> sessions(Actor actor, UUID currentSessionId) {
        return sessions.findActiveByUser(actor.userId(), clock.instant()).stream()
                .sorted(Comparator.comparing(UserSession::lastUsedAt).reversed())
                .map(s -> new SessionView(s.publicId(), s.ipAddress(), s.userAgent(), s.createdAt(), s.lastUsedAt(),
                        s.expiresAt(), s.publicId().equals(currentSessionId)))
                .toList();
    }

    @Override
    public void revokeSession(Actor actor, UUID sessionId, RequestContext context) {
        UserSession session = sessions.findByPublicId(sessionId)
                .filter(s -> s.userId() == actor.userId())
                .orElseThrow(() -> new NotFoundException("Session", sessionId));
        session.revoke("USER_REVOKED", clock.instant());
        sessions.save(session);
        audit(load(actor), AuditAction.SESSION_REVOKED, context, Map.of("session", sessionId.toString()));
    }

    private User load(Actor actor) {
        return users.findById(actor.userId()).orElseThrow(() -> new NotFoundException("User", actor.userId()));
    }

    private void audit(User user, AuditAction action, RequestContext context, Map<String, String> details) {
        auditTrail.record(new AuditEntry(user.organizationId(), user.id(), user.email(), action, "USER",
                user.publicId().toString(), AuditOutcome.SUCCESS, context.ipAddress(), context.userAgent(), details,
                clock.instant()));
    }
}
