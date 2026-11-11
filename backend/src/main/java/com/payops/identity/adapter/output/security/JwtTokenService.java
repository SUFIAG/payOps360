package com.payops.identity.adapter.output.security;

import com.payops.identity.adapter.config.SecurityProperties;
import com.payops.identity.application.port.output.TokenService;
import com.payops.identity.domain.Permission;
import com.payops.identity.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * HS512-signed JWTs. Access tokens are short-lived (15 min) and bound to a
 * server-side session id ({@code sid}) so they can be revoked. Every token
 * carries a {@code typ} claim; tokens of one type are never accepted as another.
 */
@Component
public class JwtTokenService implements TokenService {

    static final String TYPE_ACCESS = "access";
    static final String TYPE_MFA = "mfa_challenge";
    private static final String DEV_KEY_MARKER = "ZGV2LW9ubHkt";

    private final SecretKey key;
    private final SecurityProperties.Jwt settings;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public JwtTokenService(SecurityProperties properties, Environment environment, Clock clock) {
        this.settings = properties.jwt();
        this.clock = clock;
        String secret = settings.secret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("payops.security.jwt.secret (JWT_SECRET) must be set");
        }
        if (secret.startsWith(DEV_KEY_MARKER) && !environment.acceptsProfiles(Profiles.of("dev", "test"))) {
            throw new IllegalStateException("The development JWT key must not be used outside the dev profile");
        }
        byte[] raw = Base64.getDecoder().decode(secret);
        if (raw.length < 32) {
            throw new IllegalStateException("JWT_SECRET must be at least 256 bits");
        }
        this.key = Keys.hmacShaKeyFor(raw.length >= 64 ? raw : sha512(raw));
    }

    @Override
    public IssuedToken issueAccessToken(User user, UUID sessionId) {
        Instant now = clock.instant();
        Instant expiry = now.plus(settings.accessTokenTtl());
        List<String> permissions = user.role().permissions().stream().map(Permission::name).sorted().toList();
        String token = Jwts.builder()
                .issuer(settings.issuer())
                .subject(String.valueOf(user.id()))
                .id(UUID.randomUUID().toString())
                .claim("typ", TYPE_ACCESS)
                .claim("org", user.organizationId())
                .claim("email", user.email())
                .claim("role", user.role().name())
                .claim("perms", permissions)
                .claim("sid", sessionId.toString())
                .claim("pwd_change", user.mustChangePassword())
                .issuedAt(Date.from(now))
                .notBefore(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(key)
                .compact();
        return new IssuedToken(token, expiry);
    }

    @Override
    public IssuedToken issueMfaChallengeToken(User user) {
        Instant now = clock.instant();
        Instant expiry = now.plus(settings.mfaTokenTtl());
        String token = Jwts.builder()
                .issuer(settings.issuer())
                .subject(String.valueOf(user.id()))
                .id(UUID.randomUUID().toString())
                .claim("typ", TYPE_MFA)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(key)
                .compact();
        return new IssuedToken(token, expiry);
    }

    @Override
    public Optional<Long> verifyMfaChallengeToken(String token) {
        return parse(token, TYPE_MFA).map(claims -> Long.parseLong(claims.getSubject()));
    }

    /**
     * Validates signature, issuer, expiry and token type of an access token.
     */
    public Optional<AccessClaims> parseAccessToken(String token) {
        return parse(token, TYPE_ACCESS).map(claims -> new AccessClaims(
                Long.parseLong(claims.getSubject()),
                claims.get("org", Number.class).longValue(),
                claims.get("email", String.class),
                claims.get("role", String.class),
                readPermissions(claims),
                UUID.fromString(claims.get("sid", String.class)),
                Boolean.TRUE.equals(claims.get("pwd_change", Boolean.class))));
    }

    @Override
    public String newRefreshToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @Override
    public String hashOpaqueToken(String token) {
        return HexFormat.of().formatHex(sha256(token.getBytes(StandardCharsets.UTF_8)));
    }

    private Optional<Claims> parse(String token, String expectedType) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(settings.issuer())
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return expectedType.equals(claims.get("typ", String.class)) ? Optional.of(claims) : Optional.empty();
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> readPermissions(Claims claims) {
        Object perms = claims.get("perms");
        return perms instanceof List<?> list ? (List<String>) list : List.of();
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] sha512(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-512").digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public record AccessClaims(long userId, long organizationId, String email, String role, List<String> permissions,
                               UUID sessionId, boolean passwordChangeRequired) {
    }
}
