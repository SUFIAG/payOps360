package com.payops.identity.domain;

import java.time.Duration;

/**
 * What a one-time e-mail token authorises, with its lifetime. Lifetimes follow
 * the usual risk trade-off: a password reset link is short-lived, an
 * invitation must survive a weekend.
 */
public enum TokenPurpose {
    PASSWORD_RESET(Duration.ofMinutes(30)),
    INVITATION(Duration.ofHours(72)),
    EMAIL_VERIFICATION(Duration.ofHours(24));

    private final Duration ttl;

    TokenPurpose(Duration ttl) {
        this.ttl = ttl;
    }

    public Duration ttl() {
        return ttl;
    }
}
