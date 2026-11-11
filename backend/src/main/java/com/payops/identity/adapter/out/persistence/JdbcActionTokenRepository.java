package com.payops.identity.adapter.out.persistence;

import com.payops.identity.application.port.out.ActionTokenRepository;
import com.payops.identity.domain.ActionToken;
import com.payops.identity.domain.TokenPurpose;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

/**
 * One-time e-mail tokens (hashes only) implemented with plain JDBC.
 */
@Component
@RequiredArgsConstructor
class JdbcActionTokenRepository implements ActionTokenRepository {

    private static final RowMapper<ActionToken> MAPPER = (rs, i) -> new ActionToken(
            rs.getLong("id"),
            rs.getLong("user_id"),
            TokenPurpose.valueOf(rs.getString("purpose")),
            rs.getString("token_hash"),
            rs.getTimestamp("expires_at").toInstant(),
            rs.getTimestamp("consumed_at") == null ? null : rs.getTimestamp("consumed_at").toInstant(),
            rs.getTimestamp("created_at").toInstant());

    private final JdbcTemplate jdbc;

    @Override
    public ActionToken save(ActionToken token) {
        Long id = jdbc.queryForObject("INSERT INTO user_action_tokens (user_id, purpose, token_hash, expires_at, created_at) "
                        + "VALUES (?, ?, ?, ?, ?) RETURNING id", Long.class,
                token.userId(), token.purpose().name(), token.tokenHash(), Timestamp.from(token.expiresAt()),
                Timestamp.from(token.createdAt()));
        return new ActionToken(id, token.userId(), token.purpose(), token.tokenHash(), token.expiresAt(),
                token.consumedAt(), token.createdAt());
    }

    @Override
    public Optional<ActionToken> findByHash(String tokenHash) {
        return jdbc.query("SELECT id, user_id, purpose, token_hash, expires_at, consumed_at, created_at "
                + "FROM user_action_tokens WHERE token_hash = ?", MAPPER, tokenHash).stream().findFirst();
    }

    @Override
    public boolean markConsumed(long tokenId, Instant now) {
        return jdbc.update("UPDATE user_action_tokens SET consumed_at = ? WHERE id = ? AND consumed_at IS NULL "
                + "AND expires_at > ?", Timestamp.from(now), tokenId, Timestamp.from(now)) == 1;
    }

    @Override
    public int invalidateOutstanding(long userId, TokenPurpose purpose, Instant now) {
        // Expire rather than consume, so "used" and "superseded" stay distinguishable for forensics.
        return jdbc.update("UPDATE user_action_tokens SET expires_at = ? WHERE user_id = ? AND purpose = ? "
                + "AND consumed_at IS NULL AND expires_at > ?", Timestamp.from(now), userId, purpose.name(),
                Timestamp.from(now));
    }

    @Override
    public Optional<Instant> lastIssuedAt(long userId, TokenPurpose purpose) {
        return jdbc.queryForList("SELECT max(created_at) FROM user_action_tokens WHERE user_id = ? AND purpose = ?",
                        Timestamp.class, userId, purpose.name()).stream()
                .filter(java.util.Objects::nonNull)
                .map(Timestamp::toInstant)
                .findFirst();
    }
}
