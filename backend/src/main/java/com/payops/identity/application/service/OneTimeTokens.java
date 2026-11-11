package com.payops.identity.application.service;

import com.payops.identity.application.port.out.ActionTokenRepository;
import com.payops.identity.domain.ActionToken;
import com.payops.identity.domain.InvalidTokenException;
import com.payops.identity.domain.TokenPurpose;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * Issues and redeems single-use e-mail tokens: 32 random bytes (256 bits),
 * URL-safe Base64 encoded; only the SHA-256 hash is persisted. Issuing a new
 * token supersedes any outstanding token of the same purpose.
 */
@Component
@RequiredArgsConstructor
public class OneTimeTokens {

    private static final int TOKEN_BYTES = 32;
    /** Rejects absurd input before hashing; real tokens are 43 characters. */
    private static final int MAX_TOKEN_LENGTH = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ActionTokenRepository repository;
    private final Clock clock;

    /** @return the raw token; it is never stored and must only be placed in the e-mail link */
    public String issue(long userId, TokenPurpose purpose) {
        Instant now = clock.instant();
        repository.invalidateOutstanding(userId, purpose, now);
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        repository.save(ActionToken.issue(userId, purpose, raw, now));
        return raw;
    }

    /** Looks a token up without using it. Unknown, expired, used or wrong-purpose tokens are rejected alike. */
    public ActionToken resolve(String rawToken, TokenPurpose purpose) {
        if (rawToken == null || rawToken.isBlank() || rawToken.length() > MAX_TOKEN_LENGTH) {
            throw new InvalidTokenException();
        }
        return repository.findByHash(ActionToken.hash(rawToken.trim()))
                .filter(t -> t.purpose() == purpose)
                .filter(t -> t.isUsable(clock.instant()))
                .orElseThrow(InvalidTokenException::new);
    }

    /** Marks the token used; fails if a concurrent request redeemed it first. */
    public void consume(ActionToken token) {
        if (token.id() == null || !repository.markConsumed(token.id(), clock.instant())) {
            throw new InvalidTokenException();
        }
    }

    /** Throttle for "send me another link" requests. */
    public boolean issuedWithin(long userId, TokenPurpose purpose, Duration window) {
        Instant threshold = clock.instant().minus(window);
        return repository.lastIssuedAt(userId, purpose).map(t -> t.isAfter(threshold)).orElse(false);
    }

    public void revokeAll(long userId, TokenPurpose purpose) {
        repository.invalidateOutstanding(userId, purpose, clock.instant());
    }
}
