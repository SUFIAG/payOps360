package com.payops.notification.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * One message to one channel in the delivery outbox.
 */
public record NotificationDelivery(long id, UUID publicId, long organizationId, Long channelId, String channelName,
                                   ChannelType channelType, Long ruleId, Source source, String eventKey,
                                   NotificationMessage message, Status status, int attempts, String lastError,
                                   Instant nextAttemptAt, Instant createdAt, Instant sentAt) {

    public enum Status { PENDING, SENT, FAILED }

    public enum Source { RULE, ESCALATION, TEST }

    public static final int MAX_ATTEMPTS = 5;
    private static final Duration MAX_BACKOFF = Duration.ofMinutes(30);

    /** Exponential back-off after a failed attempt: 30s, 1, 2, 4 ... minutes, capped at 30 minutes. */
    public static Duration backoffAfter(int attempts) {
        Duration delay = Duration.ofSeconds(30L << Math.min(Math.max(attempts - 1, 0), 10));
        return delay.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : delay;
    }
}
