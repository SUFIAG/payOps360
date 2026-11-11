package com.payops.notification.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * A message persisted in the e-mail outbox. Bodies may be null once a
 * delivered message has been redacted (outside the dev profile).
 */
public record OutboxEmail(long id, UUID publicId, Long organizationId, String recipient, String subject,
                          String category, String textBody, String htmlBody, EmailStatus status, int attempts,
                          String lastError, Instant createdAt, Instant sentAt) {

    public static final int MAX_ATTEMPTS = 5;
    private static final Duration MAX_BACKOFF = Duration.ofHours(1);

    /** Exponential back-off after a failed attempt: 1, 2, 4, 8 ... minutes, capped at one hour. */
    public static Duration backoffAfter(int attempts) {
        Duration delay = Duration.ofMinutes(1L << Math.min(Math.max(attempts - 1, 0), 10));
        return delay.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : delay;
    }
}
