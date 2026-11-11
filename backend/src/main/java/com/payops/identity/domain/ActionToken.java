package com.payops.identity.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Single-use token sent by e-mail (password reset, invitation, e-mail
 * verification). Only the SHA-256 hash of the raw token is ever stored, so a
 * database leak does not yield usable links.
 */
public record ActionToken(Long id, long userId, TokenPurpose purpose, String tokenHash, Instant expiresAt,
                          Instant consumedAt, Instant createdAt) {

    public static ActionToken issue(long userId, TokenPurpose purpose, String rawToken, Instant now) {
        return new ActionToken(null, userId, purpose, hash(rawToken), now.plus(purpose.ttl()), null, now);
    }

    public boolean isUsable(Instant now) {
        return consumedAt == null && expiresAt.isAfter(now);
    }

    /** Hex-encoded SHA-256 of the raw token. */
    public static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
