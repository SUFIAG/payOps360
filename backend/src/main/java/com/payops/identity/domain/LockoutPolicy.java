package com.payops.identity.domain;

import java.time.Duration;

/**
 * Account lockout after repeated failures. The lock duration doubles for each
 * additional block of failures (progressive back-off), capped at 24 hours.
 */
public record LockoutPolicy(int maxFailedAttempts, Duration baseLockDuration) {

    private static final Duration MAX_LOCK = Duration.ofHours(24);

    public Duration lockDurationFor(int failedAttempts) {
        int blocks = Math.max(1, failedAttempts / maxFailedAttempts);
        Duration duration = baseLockDuration.multipliedBy(1L << Math.min(blocks - 1, 10));
        return duration.compareTo(MAX_LOCK) > 0 ? MAX_LOCK : duration;
    }
}
