package com.payops.identity.application.service;

import com.payops.audit.domain.AuditAction;
import com.payops.audit.domain.AuditOutcome;
import com.payops.identity.application.port.input.InvitationUseCase;
import com.payops.identity.application.port.input.SignupUseCase;
import com.payops.identity.domain.InvalidTokenException;
import com.payops.identity.domain.LockoutPolicy;
import com.payops.identity.domain.Organization;
import com.payops.identity.domain.Role;
import com.payops.identity.domain.SelfSignupDisabledException;
import com.payops.identity.domain.User;
import com.payops.identity.domain.UserStatus;
import com.payops.identity.domain.WeakPasswordException;
import com.payops.shared.application.Actor;
import com.payops.shared.application.RequestContext;
import com.payops.shared.domain.NotFoundException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static com.payops.identity.application.service.IdentityFakes.STRONG_PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OnboardingFlowsTest {

    private static final RequestContext CTX = new RequestContext("203.0.113.9", "junit");

    private final IdentityFakes fx = new IdentityFakes();

    private User activeUser(Organization org, String email) {
        return fx.users.save(User.register(org.id(), email, "Dana Whitfield", fx.hasher.hash("Old!Passw0rd#2025"),
                Role.ANALYST, false, IdentityFakes.START));
    }

    @Nested
    class ForgotPassword {

        private final PasswordRecoveryService service = new PasswordRecoveryService(fx.users, fx.sessions, fx.hasher,
                fx.history, fx.passwordRules, fx.tokens, fx.emails(IdentityFakes.onboarding(false)), fx.auditTrail,
                fx.clock);

        @Test
        void requestDoesNotRevealWhetherTheAccountExists() {
            Organization org = fx.organization("Acme");
            activeUser(org, "dana@acme.com");
            User disabled = activeUser(org, "gone@acme.com");
            disabled.disable();
            fx.users.save(disabled);

            // Same (void, exception-free) outcome for active, unknown and disabled accounts.
            assertThatCode(() -> service.requestReset("Dana@Acme.com ", CTX)).doesNotThrowAnyException();
            assertThatCode(() -> service.requestReset("nobody@acme.com", CTX)).doesNotThrowAnyException();
            assertThatCode(() -> service.requestReset("gone@acme.com", CTX)).doesNotThrowAnyException();

            assertThat(fx.emailsTo("dana@acme.com")).isOne();
            assertThat(fx.emailsTo("nobody@acme.com")).isZero();
            assertThat(fx.emailsTo("gone@acme.com")).as("disabled users cannot reset").isZero();
            assertThat(fx.audit).filteredOn(e -> e.action() == AuditAction.PASSWORD_RESET_REQUESTED)
                    .extracting(e -> e.outcome())
                    .containsExactly(AuditOutcome.SUCCESS, AuditOutcome.DENIED, AuditOutcome.DENIED);
            assertThat(fx.outbox.get(0).content().text())
                    .contains("http://localhost:3417/reset-password?token=")
                    .contains("30 minutes");
        }

        @Test
        void repeatedRequestsAreThrottledPerAccount() {
            activeUser(fx.organization("Acme"), "dana@acme.com");
            service.requestReset("dana@acme.com", CTX);
            service.requestReset("dana@acme.com", CTX);
            assertThat(fx.emailsTo("dana@acme.com")).isOne();
            fx.clock.advance(Duration.ofMinutes(2));
            service.requestReset("dana@acme.com", CTX);
            assertThat(fx.emailsTo("dana@acme.com")).isEqualTo(2);
        }

        @Test
        void resetSetsPasswordClearsLockoutRevokesSessionsAndIsSingleUse() {
            User user = activeUser(fx.organization("Acme"), "dana@acme.com");
            for (int i = 0; i < 5; i++) {
                user.registerFailedLogin(new LockoutPolicy(5, Duration.ofMinutes(15)), IdentityFakes.START);
            }
            fx.users.save(user);
            service.requestReset("dana@acme.com", CTX);
            String token = fx.tokenSentTo("dana@acme.com");

            assertThatThrownBy(() -> service.resetPassword(token, "weak", CTX)).isInstanceOf(WeakPasswordException.class);
            assertThatThrownBy(() -> service.resetPassword(token, "Old!Passw0rd#2025", CTX))
                    .as("current password cannot be reused").isInstanceOf(WeakPasswordException.class);

            service.resetPassword(token, STRONG_PASSWORD, CTX);
            User updated = fx.users.findById(user.id()).orElseThrow();
            assertThat(fx.hasher.matches(STRONG_PASSWORD, updated.passwordHash())).isTrue();
            assertThat(updated.isLocked(fx.clock.instant())).isFalse();
            assertThat(updated.failedLoginAttempts()).isZero();
            assertThat(fx.sessions.revokedForUsers).containsExactly(user.id());
            assertThat(fx.outbox).extracting(m -> m.category()).containsExactly("PASSWORD_RESET", "PASSWORD_CHANGED");
            assertThat(fx.audit).anyMatch(e -> e.action() == AuditAction.PASSWORD_RESET_COMPLETED);

            assertThatThrownBy(() -> service.resetPassword(token, "Another!Strong9Pass", CTX))
                    .isInstanceOf(InvalidTokenException.class);
        }

        @Test
        void disabledUserCannotCompleteAReset() {
            User user = activeUser(fx.organization("Acme"), "dana@acme.com");
            service.requestReset("dana@acme.com", CTX);
            String token = fx.tokenSentTo("dana@acme.com");
            user.disable();
            fx.users.save(user);
            assertThatThrownBy(() -> service.resetPassword(token, STRONG_PASSWORD, CTX))
                    .isInstanceOf(InvalidTokenException.class);
        }
    }

    @Nested
    class Invitations {

        private final InvitationService service = new InvitationService(fx.users, fx.organizations, fx.hasher,
                fx.passwordRules, fx.tokens, fx.emails(IdentityFakes.onboarding(false)), fx.auditTrail, fx.clock);

        private Actor admin(Organization org) {
            User admin = fx.users.save(User.register(org.id(), "admin@acme.com", "Ada Admin", "x", Role.ADMIN, false,
                    IdentityFakes.START));
            return new Actor(admin.id(), org.id(), admin.email(), Role.ADMIN.name());
        }

        @Test
        void invitedUserCannotSignInUntilAcceptingWithAStrongPassword() {
            Organization org = fx.organization("Acme Payments");
            var view = service.invite(admin(org), "New.Hire@Acme.com", "Noor Haddad", Role.SUPPORT, CTX);
            assertThat(view.status()).isEqualTo(UserStatus.INVITED);
            User invited = fx.users.findByEmail("new.hire@acme.com").orElseThrow();
            assertThat(invited.canAuthenticate(fx.clock.instant())).isFalse();
            assertThat(fx.outbox.get(0).subject()).contains("Ada Admin").contains("Acme Payments");

            String token = fx.tokenSentTo("new.hire@acme.com");
            InvitationUseCase.InvitationDetails details = service.describe(token);
            assertThat(details.organizationName()).isEqualTo("Acme Payments");
            assertThat(details.role()).isEqualTo(Role.SUPPORT);

            assertThatThrownBy(() -> service.accept(token, "short", CTX)).isInstanceOf(WeakPasswordException.class);
            assertThat(service.accept(token, STRONG_PASSWORD, CTX)).isEqualTo("new.hire@acme.com");

            User active = fx.users.findById(invited.id()).orElseThrow();
            assertThat(active.status()).isEqualTo(UserStatus.ACTIVE);
            assertThat(active.canAuthenticate(fx.clock.instant())).isTrue();
            assertThat(fx.audit).extracting(e -> e.action())
                    .contains(AuditAction.USER_INVITED, AuditAction.INVITATION_ACCEPTED);
            assertThatThrownBy(() -> service.accept(token, STRONG_PASSWORD, CTX)).isInstanceOf(InvalidTokenException.class);
            assertThatThrownBy(() -> service.describe(token)).isInstanceOf(InvalidTokenException.class);
        }

        @Test
        void resendSupersedesTheOldLinkAndRevokeRemovesTheAccount() {
            Organization org = fx.organization("Acme");
            Actor admin = admin(org);
            var view = service.invite(admin, "new@acme.com", "Noor Haddad", Role.ANALYST, CTX);
            String first = fx.tokenSentTo("new@acme.com");
            fx.clock.advance(Duration.ofMinutes(2));
            service.resend(admin, view.id(), CTX);
            assertThatThrownBy(() -> service.describe(first)).isInstanceOf(InvalidTokenException.class);
            String second = fx.tokenSentTo("new@acme.com");
            assertThat(service.describe(second).email()).isEqualTo("new@acme.com");

            service.revoke(admin, view.id(), CTX);
            assertThat(fx.users.findByEmail("new@acme.com")).isEmpty();
            assertThat(fx.audit).anyMatch(e -> e.action() == AuditAction.INVITATION_REVOKED);
        }

        @Test
        void invitationsAreTenantScoped() {
            Organization acme = fx.organization("Acme");
            Organization other = fx.organization("Other");
            var view = service.invite(admin(acme), "new@acme.com", "Noor Haddad", Role.ANALYST, CTX);
            Actor foreignAdmin = new Actor(999L, other.id(), "x@other.com", "ADMIN");
            assertThatThrownBy(() -> service.revoke(foreignAdmin, view.id(), CTX)).isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    class Signup {

        private SignupService service(boolean enabled) {
            return new SignupService(fx.users, fx.organizations, fx.hasher, fx.tokens,
                    fx.emails(IdentityFakes.onboarding(enabled)), fx.auditTrail, fx.identitySettings,
                    IdentityFakes.onboarding(enabled), fx.clock);
        }

        private SignupUseCase.SignupCommand command(String email) {
            return new SignupUseCase.SignupCommand("Acme Payments", "Sam Rivera", email, STRONG_PASSWORD, "usd");
        }

        @Test
        void disabledFlagMakesSignupNotFound() {
            SignupService service = service(false);
            assertThatThrownBy(() -> service.signup(command("sam@acme.com"), CTX))
                    .isInstanceOf(SelfSignupDisabledException.class)
                    .isInstanceOf(NotFoundException.class);
            assertThat(fx.organizations.count()).isZero();
            assertThat(fx.outbox).isEmpty();
            assertThat(service.config().selfSignupEnabled()).isFalse();
        }

        @Test
        void signupCreatesOrganisationAndUnverifiedAdminUntilEmailIsVerified() {
            SignupService service = service(true);
            fx.organization("Acme Payments");
            service.signup(command("Sam@Acme.com"), CTX);

            Organization org = fx.organizations.findAllActive().get(1);
            assertThat(org.slug()).startsWith("acme-payments-").isNotEqualTo("acme-payments");
            assertThat(org.baseCurrency()).isEqualTo("USD");
            assertThat(org.mfaRequired()).isTrue();
            User admin = fx.users.findByEmail("sam@acme.com").orElseThrow();
            assertThat(admin.role()).isEqualTo(Role.ADMIN);
            assertThat(admin.status()).isEqualTo(UserStatus.PENDING_VERIFICATION);
            assertThat(admin.canAuthenticate(fx.clock.instant())).isFalse();

            service.verifyEmail(fx.tokenSentTo("sam@acme.com"), CTX);
            assertThat(fx.users.findById(admin.id()).orElseThrow().status()).isEqualTo(UserStatus.ACTIVE);
            assertThat(fx.audit).extracting(e -> e.action())
                    .contains(AuditAction.ORGANIZATION_SIGNED_UP, AuditAction.EMAIL_VERIFIED);
        }

        @Test
        void signupWithRegisteredEmailCreatesNothingAndNotifiesTheOwner() {
            SignupService service = service(true);
            activeUser(fx.organization("Existing"), "sam@acme.com");
            assertThatCode(() -> service.signup(command("sam@acme.com"), CTX)).doesNotThrowAnyException();
            assertThat(fx.organizations.count()).isOne();
            assertThat(fx.outbox).singleElement().extracting(m -> m.category()).isEqualTo("SIGNUP_EXISTING_ACCOUNT");
            assertThat(fx.hasher.simulations).isOne();
        }
    }
}
