package com.payops.identity.application.service;

import com.payops.audit.application.port.input.AuditTrail;
import com.payops.audit.domain.AuditAction;
import com.payops.audit.domain.AuditEntry;
import com.payops.audit.domain.AuditOutcome;
import com.payops.identity.application.port.input.UserAdministrationUseCase;
import com.payops.identity.application.port.input.UserView;
import com.payops.identity.application.port.output.PasswordHasher;
import com.payops.identity.application.port.output.SessionRepository;
import com.payops.identity.application.port.output.UserRepository;
import com.payops.identity.domain.EmailAddress;
import com.payops.identity.domain.Role;
import com.payops.identity.domain.User;
import com.payops.shared.application.Actor;
import com.payops.shared.application.RequestContext;
import com.payops.shared.domain.ConflictException;
import com.payops.shared.domain.DomainException;
import com.payops.shared.domain.NotFoundException;
import com.payops.shared.domain.PageQuery;
import com.payops.shared.domain.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Tenant user administration. Besides e-mail invitations (see
 * {@link InvitationService}) an administrator can provision a user directly
 * with a one-time temporary password.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class UserAdministrationService implements UserAdministrationUseCase {

    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijkmnpqrstuvwxyz";
    private static final String DIGITS = "23456789";
    private static final String SYMBOLS = "!@#$%^&*-_=+?";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final SessionRepository sessions;
    private final PasswordHasher passwordHasher;
    private final RecoveryCodes recoveryCodes;
    private final AuditTrail auditTrail;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public PageResult<UserView> list(Actor actor, PageQuery page) {
        Instant now = clock.instant();
        return users.findByOrganization(actor.organizationId(), page).map(u -> UserView.of(u, now));
    }

    @Override
    public CreatedUser create(Actor actor, String email, String fullName, Role role, RequestContext context) {
        String normalized = EmailAddress.normalize(email);
        if (users.existsByEmail(normalized)) {
            throw new ConflictException("USER_EXISTS", "A user with this email already exists");
        }
        String temporaryPassword = temporaryPassword();
        User user = users.save(User.register(actor.organizationId(), normalized, fullName,
                passwordHasher.hash(temporaryPassword), role, true, clock.instant()));
        audit(actor, user, AuditAction.USER_CREATED, context, Map.of("role", role.name()));
        return new CreatedUser(UserView.of(user, clock.instant()), temporaryPassword);
    }

    @Override
    public UserView changeRole(Actor actor, UUID userId, Role role, RequestContext context) {
        User user = load(actor, userId);
        preventSelfLockout(actor, user);
        Role previous = user.role();
        user.changeRole(role);
        audit(actor, user, AuditAction.USER_ROLE_CHANGED, context, Map.of("from", previous.name(), "to", role.name()));
        revokeSessions(user, "ROLE_CHANGED");
        return view(users.save(user));
    }

    @Override
    public UserView disable(Actor actor, UUID userId, RequestContext context) {
        User user = load(actor, userId);
        preventSelfLockout(actor, user);
        user.disable();
        revokeSessions(user, "USER_DISABLED");
        audit(actor, user, AuditAction.USER_DISABLED, context, Map.of());
        return view(users.save(user));
    }

    @Override
    public UserView enable(Actor actor, UUID userId, RequestContext context) {
        User user = load(actor, userId);
        user.enable();
        audit(actor, user, AuditAction.USER_ENABLED, context, Map.of());
        return view(users.save(user));
    }

    @Override
    public UserView unlock(Actor actor, UUID userId, RequestContext context) {
        User user = load(actor, userId);
        user.unlock();
        audit(actor, user, AuditAction.USER_UNLOCKED, context, Map.of());
        return view(users.save(user));
    }

    @Override
    public CreatedUser resetPassword(Actor actor, UUID userId, RequestContext context) {
        User user = load(actor, userId);
        String temporaryPassword = temporaryPassword();
        user.resetPassword(passwordHasher.hash(temporaryPassword), clock.instant());
        revokeSessions(user, "PASSWORD_RESET");
        audit(actor, user, AuditAction.PASSWORD_RESET, context, Map.of());
        return new CreatedUser(view(users.save(user)), temporaryPassword);
    }

    @Override
    public UserView resetMfa(Actor actor, UUID userId, RequestContext context) {
        User user = load(actor, userId);
        user.disableMfa();
        recoveryCodes.deleteAll(user.id());
        revokeSessions(user, "MFA_RESET");
        audit(actor, user, AuditAction.MFA_RESET, context, Map.of());
        return view(users.save(user));
    }

    private User load(Actor actor, UUID userId) {
        return users.findByPublicId(actor.organizationId(), userId)
                .orElseThrow(() -> new NotFoundException("User", userId));
    }

    private void preventSelfLockout(Actor actor, User user) {
        if (user.id() == actor.userId()) {
            throw new DomainException("SELF_MODIFICATION", "You cannot change your own role or status");
        }
    }

    private void revokeSessions(User user, String reason) {
        sessions.revokeAllForUser(user.id(), reason, clock.instant());
    }

    private UserView view(User user) {
        return UserView.of(user, clock.instant());
    }

    private void audit(Actor actor, User target, AuditAction action, RequestContext context, Map<String, String> details) {
        auditTrail.record(new AuditEntry(actor.organizationId(), actor.userId(), actor.email(), action, "USER",
                target.publicId().toString(), AuditOutcome.SUCCESS, context.ipAddress(), context.userAgent(),
                details, clock.instant()));
    }

    /** 16 characters guaranteed to satisfy the password policy. */
    static String temporaryPassword() {
        StringBuilder sb = new StringBuilder();
        String all = UPPER + LOWER + DIGITS + SYMBOLS;
        sb.append(UPPER.charAt(RANDOM.nextInt(UPPER.length())));
        sb.append(LOWER.charAt(RANDOM.nextInt(LOWER.length())));
        sb.append(DIGITS.charAt(RANDOM.nextInt(DIGITS.length())));
        sb.append(SYMBOLS.charAt(RANDOM.nextInt(SYMBOLS.length())));
        while (sb.length() < 16) {
            sb.append(all.charAt(RANDOM.nextInt(all.length())));
        }
        char[] chars = sb.toString().toCharArray();
        for (int i = chars.length - 1; i > 0; i--) {
            int j = RANDOM.nextInt(i + 1);
            char tmp = chars[i];
            chars[i] = chars[j];
            chars[j] = tmp;
        }
        return new String(chars);
    }
}
