package com.payops.identity.application.port.output;

import com.payops.identity.domain.ActionToken;
import com.payops.identity.domain.TokenPurpose;

import java.time.Instant;
import java.util.Optional;

public interface ActionTokenRepository {

    ActionToken save(ActionToken token);

    Optional<ActionToken> findByHash(String tokenHash);

    /**
     * Atomically marks the token used. Returns false when it was already
     * consumed or has expired (protects against concurrent double use).
     */
    boolean markConsumed(long tokenId, Instant now);

    /** Expires every outstanding token of this purpose for the user (a newer link supersedes older ones). */
    int invalidateOutstanding(long userId, TokenPurpose purpose, Instant now);

    Optional<Instant> lastIssuedAt(long userId, TokenPurpose purpose);
}
