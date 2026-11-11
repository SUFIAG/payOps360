package com.payops.identity.application.service;

import com.payops.audit.application.port.input.AuditTrail;
import com.payops.audit.domain.AuditAction;
import com.payops.audit.domain.AuditEntry;
import com.payops.audit.domain.AuditOutcome;
import com.payops.identity.application.port.input.InvitationUseCase;
import com.payops.identity.application.port.input.UserView;
import com.payops.identity.application.port.output.OrganizationRepository;
import com.payops.identity.application.port.output.PasswordHasher;
import com.payops.identity.application.port.output.UserRepository;
import com.payops.identity.domain.ActionToken;
import com.payops.identity.domain.EmailAddress;
import com.payops.identity.domain.InvalidTokenException;
import com.payops.identity.domain.Organization;
import com.payops.identity.domain.Role;
import com.payops.identity.domain.TokenPurpose;
import com.payops.identity.domain.User;
import com.payops.shared.application.Actor;
import com.payops.shared.application.RequestContext;
import com.payops.shared.domain.ConflictException;
import com.payops.shared.domain.DomainException;
import com.payops.shared.domain.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

/**
 * Invitation lifecycle: invite → (resend | revoke) → accept. An invited user
 * holds an unusable password and cannot sign in until they accept through
 * the single-use, 72-hour link and choose a password that meets the policy.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class InvitationService implements InvitationUseCase {

    private static final Duration RESEND_COOLDOWN = Duration.ofMinutes(1);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final OrganizationRepository organizations;
    private final PasswordHasher passwordHasher;
    private final PasswordRules passwordRules;
    private final OneTimeTokens tokens;
    private final IdentityEmails emails;
    private final AuditTrail auditTrail;
    private final Clock clock;

    @Override
    public UserView invite(Actor actor, String email, String fullName, Role role, RequestContext context) {
        String normalized = EmailAddress.normalize(email);
        if (users.existsByEmail(normalized)) {
            throw new ConflictException("USER_EXISTS", "A user with this email already exists");
        }
        Instant now = clock.instant();
        User user = users.save(User.invite(actor.organizationId(), normalized, fullName,
                passwordHasher.hash(unusableSecret()), role, now));
        sendInvitation(actor, user);
        audit(actor, user, AuditAction.USER_INVITED, context, Map.of("role", role.name()));
        return UserView.of(user, now);
    }

    @Override
    public UserView resend(Actor actor, UUID userId, RequestContext context) {
        User user = loadInvited(actor, userId);
        if (tokens.issuedWithin(user.id(), TokenPurpose.INVITATION, RESEND_COOLDOWN)) {
            throw new DomainException("RESEND_TOO_SOON", "An invitation was sent less than a minute ago");
        }
        sendInvitation(actor, user);
        audit(actor, user, AuditAction.INVITATION_RESENT, context, Map.of());
        return UserView.of(user, clock.instant());
    }

    @Override
    public void revoke(Actor actor, UUID userId, RequestContext context) {
        User user = loadInvited(actor, userId);
        // Tokens cascade with the user row, so the link dies with the account.
        users.delete(user);
        audit(actor, user, AuditAction.INVITATION_REVOKED, context, Map.of("email", user.email()));
    }

    @Override
    @Transactional(readOnly = true)
    public InvitationDetails describe(String rawToken) {
        ActionToken token = tokens.resolve(rawToken, TokenPurpose.INVITATION);
        User user = invitee(token);
        Organization organization = organizations.findById(user.organizationId())
                .orElseThrow(InvalidTokenException::new);
        return new InvitationDetails(organization.name(), user.email(), user.fullName(), user.role(),
                token.expiresAt());
    }

    @Override
    public String accept(String rawToken, String password, RequestContext context) {
        ActionToken token = tokens.resolve(rawToken, TokenPurpose.INVITATION);
        User user = invitee(token);
        passwordRules.enforceNewPassword(user, password);
        tokens.consume(token);
        user.acceptInvitation(passwordHasher.hash(password), clock.instant());
        users.save(user);
        auditTrail.record(new AuditEntry(user.organizationId(), user.id(), user.email(),
                AuditAction.INVITATION_ACCEPTED, "USER", user.publicId().toString(), AuditOutcome.SUCCESS,
                context.ipAddress(), context.userAgent(), Map.of("role", user.role().name()), clock.instant()));
        return user.email();
    }

    // ------------------------------------------------------------------------

    private void sendInvitation(Actor actor, User invitee) {
        String token = tokens.issue(invitee.id(), TokenPurpose.INVITATION);
        String organizationName = organizations.findById(actor.organizationId()).map(Organization::name)
                .orElse("your organisation");
        String inviterName = users.findById(actor.userId()).map(User::fullName).orElse(actor.email());
        emails.invitation(invitee, organizationName, inviterName, token);
    }

    private User loadInvited(Actor actor, UUID userId) {
        User user = users.findByPublicId(actor.organizationId(), userId)
                .orElseThrow(() -> new NotFoundException("User", userId));
        if (!user.isInvited()) {
            throw new DomainException("NOT_INVITED", "This user has no pending invitation");
        }
        return user;
    }

    private User invitee(ActionToken token) {
        return users.findById(token.userId()).filter(User::isInvited).orElseThrow(InvalidTokenException::new);
    }

    private void audit(Actor actor, User target, AuditAction action, RequestContext context, Map<String, String> details) {
        auditTrail.record(new AuditEntry(actor.organizationId(), actor.userId(), actor.email(), action, "USER",
                target.publicId().toString(), AuditOutcome.SUCCESS, context.ipAddress(), context.userAgent(),
                details, clock.instant()));
    }

    /** Placeholder credential for invited users: hashed and immediately forgotten. */
    private static String unusableSecret() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
