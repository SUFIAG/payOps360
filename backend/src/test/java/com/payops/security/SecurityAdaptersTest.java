package com.payops.security;

import com.payops.audit.domain.AuditAction;
import com.payops.audit.domain.AuditEntry;
import com.payops.audit.domain.AuditOutcome;
import com.payops.audit.domain.StoredAuditEntry;
import com.payops.identity.adapter.config.SecurityProperties;
import com.payops.identity.adapter.output.security.JwtTokenService;
import com.payops.identity.domain.Role;
import com.payops.identity.domain.User;
import com.payops.identity.domain.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityAdaptersTest {

    private static final Instant NOW = Instant.parse("2026-10-07T10:00:00Z");
    private static final String SECRET = Base64.getEncoder().encodeToString("unit-test-signing-key-with-at-least-256-bits!!".getBytes());

    @Test
    void accessTokensAreTypedBoundToSessionAndExpire() {
        JwtTokenService tokens = tokenService(Clock.fixed(NOW, ZoneOffset.UTC), "prod");
        User user = new User(42L, UUID.randomUUID(), 9L, "ops@acme.com", "Ops User", "hash", Role.OPERATIONS_MANAGER,
                UserStatus.ACTIVE, 0, null, false, NOW, false, null, null, null, null, NOW, 0);
        UUID session = UUID.randomUUID();
        String access = tokens.issueAccessToken(user, session).value();
        String mfa = tokens.issueMfaChallengeToken(user).value();

        var claims = tokens.parseAccessToken(access).orElseThrow();
        assertThat(claims.sessionId()).isEqualTo(session);
        assertThat(claims.permissions()).contains("PAYMENT_WRITE").doesNotContain("USER_MANAGE");
        assertThat(tokens.parseAccessToken(mfa)).as("MFA challenge token is not an access token").isEmpty();
        assertThat(tokens.verifyMfaChallengeToken(access)).as("access token is not an MFA token").isEmpty();

        JwtTokenService later = tokenService(Clock.fixed(NOW.plus(Duration.ofMinutes(16)), ZoneOffset.UTC), "prod");
        assertThat(later.parseAccessToken(access)).isEmpty();
        assertThat(tokens.parseAccessToken(access.substring(0, access.length() - 2) + "xx")).isEmpty();
    }

    @Test
    void developmentKeyIsRefusedOutsideDev() {
        String devKey = "ZGV2LW9ubHktcGF5b3BzMzYwLWp3dC1zaWduaW5nLWtleS1kby1ub3QtdXNlLWluLXByb2Q=";
        assertThatThrownBy(() -> new JwtTokenService(properties(devKey), env("prod"), Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new JwtTokenService(properties(""), env("prod"), Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void refreshTokensAreHighEntropyAndOnlyHashesAreComparable() {
        JwtTokenService tokens = tokenService(Clock.systemUTC(), "dev");
        String a = tokens.newRefreshToken();
        assertThat(a).isNotEqualTo(tokens.newRefreshToken()).hasSizeGreaterThanOrEqualTo(43);
        assertThat(tokens.hashOpaqueToken(a)).hasSize(64).isEqualTo(tokens.hashOpaqueToken(a));
    }

    @Test
    void auditHashChainDetectsTampering() {
        AuditEntry first = entry("first");
        AuditEntry second = entry("second");
        String h1 = first.chainHash(null);
        String h2 = second.chainHash(h1);
        assertThat(new StoredAuditEntry(1, first, null, h1).isIntact()).isTrue();
        assertThat(new StoredAuditEntry(2, second, h1, h2).isIntact()).isTrue();
        AuditEntry tampered = entry("tampered");
        assertThat(new StoredAuditEntry(2, tampered, h1, h2).isIntact()).isFalse();
    }

    private static AuditEntry entry(String detail) {
        return new AuditEntry(1L, 2L, "a@b.com", AuditAction.LOGIN_SUCCESS, "USER", "2", AuditOutcome.SUCCESS,
                "10.0.0.1", "ua", Map.of("note", detail), NOW);
    }

    private static JwtTokenService tokenService(Clock clock, String profile) {
        return new JwtTokenService(properties(SECRET), env(profile), clock);
    }

    private static MockEnvironment env(String profile) {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles(profile);
        return env;
    }

    private static SecurityProperties properties(String secret) {
        return new SecurityProperties(
                new SecurityProperties.Jwt(secret, "payops360", Duration.ofMinutes(15), Duration.ofMinutes(5)),
                Duration.ofHours(12), null, true, List.of("http://localhost:3000"),
                new SecurityProperties.Lockout(5, Duration.ofMinutes(15), 30),
                new SecurityProperties.RateLimit(10, 600),
                new SecurityProperties.Password(12, 5, 90));
    }
}
