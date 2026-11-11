package com.payops.identity.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * A refresh-token session. Each refresh rotates the token: the old session
 * row is revoked and a new one is created in the same family. Presenting an
 * already-rotated token is treated as theft and revokes the whole family.
 */
public class UserSession {

    private final Long id;
    private final UUID publicId;
    private final long userId;
    private final UUID familyId;
    private final String refreshTokenHash;
    private final String ipAddress;
    private final String userAgent;
    private final Instant createdAt;
    private Instant lastUsedAt;
    private final Instant expiresAt;
    private Instant revokedAt;
    private String revokedReason;

    @SuppressWarnings("java:S107")
    public UserSession(Long id, UUID publicId, long userId, UUID familyId, String refreshTokenHash,
                       String ipAddress, String userAgent, Instant createdAt, Instant lastUsedAt,
                       Instant expiresAt, Instant revokedAt, String revokedReason) {
        this.id = id;
        this.publicId = publicId;
        this.userId = userId;
        this.familyId = familyId;
        this.refreshTokenHash = refreshTokenHash;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.createdAt = createdAt;
        this.lastUsedAt = lastUsedAt;
        this.expiresAt = expiresAt;
        this.revokedAt = revokedAt;
        this.revokedReason = revokedReason;
    }

    public static UserSession start(long userId, UUID familyId, String refreshTokenHash, String ipAddress,
                                    String userAgent, Instant now, Instant expiresAt) {
        return new UserSession(null, UUID.randomUUID(), userId, familyId, refreshTokenHash, ipAddress, userAgent,
                now, now, expiresAt, null, null);
    }

    public boolean isActive(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }

    public void revoke(String reason, Instant now) {
        if (revokedAt == null) {
            revokedAt = now;
            revokedReason = reason;
        }
    }

    public Long id() { return id; }
    public UUID publicId() { return publicId; }
    public long userId() { return userId; }
    public UUID familyId() { return familyId; }
    public String refreshTokenHash() { return refreshTokenHash; }
    public String ipAddress() { return ipAddress; }
    public String userAgent() { return userAgent; }
    public Instant createdAt() { return createdAt; }
    public Instant lastUsedAt() { return lastUsedAt; }
    public Instant expiresAt() { return expiresAt; }
    public Instant revokedAt() { return revokedAt; }
    public String revokedReason() { return revokedReason; }
}
