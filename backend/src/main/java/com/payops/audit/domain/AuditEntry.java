package com.payops.audit.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

/**
 * Immutable audit record. Entries of one tenant form a hash chain: each
 * entry's hash covers its own content and the previous entry's hash, so any
 * modification or deletion breaks verification.
 */
public record AuditEntry(
        Long organizationId,
        Long actorUserId,
        String actorEmail,
        AuditAction action,
        String resourceType,
        String resourceId,
        AuditOutcome outcome,
        String ipAddress,
        String userAgent,
        Map<String, String> details,
        Instant occurredAt) {

    public AuditEntry {
        details = details == null ? Map.of() : Map.copyOf(details);
    }

    public String chainHash(String previousHash) {
        String canonical = String.join("|",
                String.valueOf(previousHash),
                String.valueOf(organizationId),
                String.valueOf(actorUserId),
                String.valueOf(actorEmail),
                action.name(),
                String.valueOf(resourceType),
                String.valueOf(resourceId),
                outcome.name(),
                String.valueOf(ipAddress),
                new TreeMap<>(details).toString(),
                String.valueOf(occurredAt.toEpochMilli()));
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
