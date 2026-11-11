package com.payops.identity.domain;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdentityDomainTest {

    private static final Instant NOW = Instant.parse("2026-10-07T10:00:00Z");

    @Test
    void totpMatchesRfc6238ReferenceVector() {
        // RFC 6238 Appendix B, SHA-1 secret "12345678901234567890", T = 59s -> 94287082 (8 digits) -> 287082 (6 digits)
        String secret = Totp.base32Encode("12345678901234567890".getBytes());
        assertThat(Totp.codeAt(secret, Instant.ofEpochSecond(59))).isEqualTo("287082");
        assertThat(Totp.codeAt(secret, Instant.ofEpochSecond(1111111109))).isEqualTo("081804");
    }

    @Test
    void totpAcceptsOneStepOfDriftAndRejectsGarbage() {
        String secret = Totp.generateSecret();
        String previous = Totp.codeAt(secret, NOW.minusSeconds(30));
        assertThat(Totp.verify(secret, previous, NOW)).isPresent();
        assertThat(Totp.verify(secret, Totp.codeAt(secret, NOW.minusSeconds(120)), NOW)).isEmpty();
        assertThat(Totp.verify(secret, "12ab56", NOW)).isEmpty();
        assertThat(Totp.verify(secret, null, NOW)).isEmpty();
    }

    @Test
    void totpStepCannotBeReplayed() {
        User user = user();
        String secret = Totp.generateSecret();
        user.stageMfaSecret("enc");
        OptionalLong step = Totp.verify(secret, Totp.codeAt(secret, NOW), NOW);
        user.activateMfa(step.getAsLong() - 1);
        assertThat(user.acceptTotpStep(step.getAsLong())).isTrue();
        assertThat(user.acceptTotpStep(step.getAsLong())).isFalse();
    }

    @Test
    void accountLocksAfterMaxFailuresWithProgressiveBackoff() {
        LockoutPolicy policy = new LockoutPolicy(5, Duration.ofMinutes(15));
        User user = user();
        for (int i = 0; i < 4; i++) {
            assertThat(user.registerFailedLogin(policy, NOW)).isFalse();
        }
        assertThat(user.registerFailedLogin(policy, NOW)).isTrue();
        assertThat(user.isLocked(NOW.plus(Duration.ofMinutes(14)))).isTrue();
        assertThat(user.isLocked(NOW.plus(Duration.ofMinutes(16)))).isFalse();
        assertThat(policy.lockDurationFor(10)).isEqualTo(Duration.ofMinutes(30));
        assertThat(policy.lockDurationFor(500)).isEqualTo(Duration.ofHours(24));
    }

    @Test
    void successfulLoginResetsFailuresAndFlagsExpiredPasswords() {
        User user = user();
        user.registerFailedLogin(new LockoutPolicy(5, Duration.ofMinutes(15)), NOW);
        user.registerSuccessfulLogin("10.0.0.1", NOW.plus(Duration.ofDays(91)), Duration.ofDays(90));
        assertThat(user.failedLoginAttempts()).isZero();
        assertThat(user.mustChangePassword()).isTrue();
    }

    @Test
    void passwordPolicyEnforcesStrengthAndPersonalData() {
        PasswordPolicy policy = new PasswordPolicy(12, 5);
        assertThat(policy.violations("Str0ng!Ops#Pass", "jane@acme.com", "Jane Doe")).isEmpty();
        assertThat(policy.violations("short1!A", null, null)).anyMatch(v -> v.contains("12 characters"));
        assertThat(policy.violations("alllowercase123!", null, null)).anyMatch(v -> v.contains("uppercase"));
        assertThat(policy.violations("NoSpecialChars123", null, null)).anyMatch(v -> v.contains("special"));
        assertThat(policy.violations("MyPassword123!", null, null)).anyMatch(v -> v.contains("commonly used"));
        assertThat(policy.violations("Jane!Secure2026x", "jane@acme.com", "Jane Doe")).anyMatch(v -> v.contains("email"));
        assertThat(policy.violations("Aaaaa!1bcdefgh", null, null)).anyMatch(v -> v.contains("repeat"));
        assertThatThrownBy(() -> policy.enforce("weak", null, null)).isInstanceOf(WeakPasswordException.class);
    }

    @Test
    void disabledUsersCannotAuthenticate() {
        User user = user();
        user.disable();
        assertThat(user.canAuthenticate(NOW)).isFalse();
        user.enable();
        assertThat(user.canAuthenticate(NOW)).isTrue();
    }

    @Test
    void rolesFollowLeastPrivilege() {
        assertThat(Role.ADMIN.permissions()).contains(Permission.values());
        assertThat(Role.ANALYST.permissions()).doesNotContain(Permission.USER_MANAGE, Permission.PAYMENT_WRITE);
        assertThat(Role.AUDITOR.permissions()).contains(Permission.AUDIT_READ).doesNotContain(Permission.AI_USE);
    }

    private static User user() {
        return User.register(1L, "Jane@Acme.com", "Jane Doe", "hash", Role.ANALYST, false, NOW);
    }
}
