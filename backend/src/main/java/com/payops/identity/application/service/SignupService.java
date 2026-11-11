package com.payops.identity.application.service;

import com.payops.audit.application.port.in.AuditTrail;
import com.payops.audit.domain.AuditAction;
import com.payops.audit.domain.AuditEntry;
import com.payops.audit.domain.AuditOutcome;
import com.payops.identity.application.port.in.SignupUseCase;
import com.payops.identity.application.port.out.OrganizationRepository;
import com.payops.identity.application.port.out.PasswordHasher;
import com.payops.identity.application.port.out.UserRepository;
import com.payops.identity.domain.ActionToken;
import com.payops.identity.domain.EmailAddress;
import com.payops.identity.domain.InvalidTokenException;
import com.payops.identity.domain.Organization;
import com.payops.identity.domain.Role;
import com.payops.identity.domain.SelfSignupDisabledException;
import com.payops.identity.domain.TokenPurpose;
import com.payops.identity.domain.User;
import com.payops.identity.domain.UserStatus;
import com.payops.shared.application.RequestContext;
import com.payops.shared.domain.DomainException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Organisation self-service sign-up (behind {@code payops.onboarding.self-signup-enabled})
 * and e-mail verification. The first user becomes the organisation's ADMIN in
 * PENDING_VERIFICATION state and cannot sign in until the address is confirmed.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class SignupService implements SignupUseCase {

    private static final Duration RESEND_COOLDOWN = Duration.ofMinutes(1);
    private static final char[] SLUG_SUFFIX = "23456789abcdefghjkmnpqrstuvwxyz".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final OrganizationRepository organizations;
    private final PasswordHasher passwordHasher;
    private final OneTimeTokens tokens;
    private final IdentityEmails emails;
    private final AuditTrail auditTrail;
    private final IdentitySettings identitySettings;
    private final OnboardingSettings onboardingSettings;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public OnboardingConfig config() {
        return new OnboardingConfig(onboardingSettings.selfSignupEnabled(),
                identitySettings.passwordPolicy().minLength());
    }

    @Override
    public void signup(SignupCommand command, RequestContext context) {
        if (!onboardingSettings.selfSignupEnabled()) {
            throw new SelfSignupDisabledException();
        }
        String email = EmailAddress.normalize(command.email());
        String organizationName = requireText(command.organizationName(), "organisation name");
        String fullName = requireText(command.fullName(), "full name");
        String currency = currency(command.baseCurrency());
        // Validated before the existence check so the response never depends on whether the address is known.
        identitySettings.passwordPolicy().enforce(command.password(), email, fullName);

        User existing = users.findByEmail(email).orElse(null);
        if (existing != null) {
            passwordHasher.simulateVerification();
            emails.signupForExistingAccount(email, existing.organizationId());
            audit(existing.organizationId(), existing.id(), email, AuditAction.SIGNUP_ATTEMPT_EXISTING_EMAIL,
                    AuditOutcome.DENIED, context, Map.of());
            return;
        }

        Instant now = clock.instant();
        Organization organization = organizations.save(new Organization(null, UUID.randomUUID(), organizationName,
                uniqueSlug(organizationName), onboardingSettings.newOrganizationMfaRequired(), true, currency));
        User admin = users.save(User.registerUnverified(organization.id(), email, fullName,
                passwordHasher.hash(command.password()), Role.ADMIN, now));
        emails.emailVerification(admin, organization.name(), tokens.issue(admin.id(), TokenPurpose.EMAIL_VERIFICATION));
        // The audit trail writes in its own transaction and references the organisation by foreign key,
        // so it can only be recorded once the organisation row is committed.
        afterCommit(() -> audit(organization.id(), admin.id(), email, AuditAction.ORGANIZATION_SIGNED_UP,
                AuditOutcome.SUCCESS, context, Map.of("organization", organization.publicId().toString(),
                        "slug", organization.slug(), "mfaRequired", String.valueOf(organization.mfaRequired()))));
    }

    @Override
    public void verifyEmail(String rawToken, RequestContext context) {
        ActionToken token = tokens.resolve(rawToken, TokenPurpose.EMAIL_VERIFICATION);
        User user = users.findById(token.userId())
                .filter(u -> u.status() == UserStatus.PENDING_VERIFICATION)
                .orElseThrow(InvalidTokenException::new);
        tokens.consume(token);
        user.verifyEmail();
        users.save(user);
        audit(user.organizationId(), user.id(), user.email(), AuditAction.EMAIL_VERIFIED, AuditOutcome.SUCCESS,
                context, Map.of());
    }

    @Override
    public void resendVerification(String email, RequestContext context) {
        String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        User user = users.findByEmail(normalized).orElse(null);
        if (user == null || user.status() != UserStatus.PENDING_VERIFICATION
                || tokens.issuedWithin(user.id(), TokenPurpose.EMAIL_VERIFICATION, RESEND_COOLDOWN)) {
            return;
        }
        String organizationName = organizations.findById(user.organizationId()).map(Organization::name).orElse("");
        emails.emailVerification(user, organizationName, tokens.issue(user.id(), TokenPurpose.EMAIL_VERIFICATION));
        audit(user.organizationId(), user.id(), user.email(), AuditAction.EMAIL_VERIFICATION_RESENT,
                AuditOutcome.SUCCESS, context, Map.of());
    }

    // ------------------------------------------------------------------------

    private String uniqueSlug(String organizationName) {
        String base = Organization.slugify(organizationName);
        String candidate = base;
        while (organizations.existsBySlug(candidate)) {
            char[] suffix = new char[5];
            for (int i = 0; i < suffix.length; i++) {
                suffix[i] = SLUG_SUFFIX[RANDOM.nextInt(SLUG_SUFFIX.length)];
            }
            candidate = base + "-" + new String(suffix);
        }
        return candidate;
    }

    private static String requireText(String value, String field) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.length() < 2 || trimmed.length() > 200) {
            throw new DomainException("INVALID_INPUT", "The " + field + " must be between 2 and 200 characters");
        }
        return trimmed;
    }

    private static String currency(String value) {
        String code = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!code.matches("[A-Z]{3}")) {
            throw new DomainException("INVALID_CURRENCY", "Base currency must be a three-letter ISO 4217 code");
        }
        return code;
    }

    private static void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private void audit(Long orgId, Long userId, String email, AuditAction action, AuditOutcome outcome,
                       RequestContext context, Map<String, String> details) {
        auditTrail.record(new AuditEntry(orgId, userId, email, action, "USER",
                userId == null ? null : String.valueOf(userId), outcome, context.ipAddress(), context.userAgent(),
                details, clock.instant()));
    }
}
