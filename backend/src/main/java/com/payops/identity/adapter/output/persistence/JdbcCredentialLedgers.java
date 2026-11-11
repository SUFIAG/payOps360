package com.payops.identity.adapter.output.persistence;

import com.payops.identity.application.port.output.LoginAttemptRepository;
import com.payops.identity.application.port.output.PasswordHistoryRepository;
import com.payops.identity.application.port.output.RecoveryCodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/**
 * Append-mostly credential ledgers (login attempts, password history,
 * MFA recovery codes) implemented with plain JDBC.
 */
@Component
@RequiredArgsConstructor
class JdbcCredentialLedgers implements LoginAttemptRepository, PasswordHistoryRepository, RecoveryCodeRepository {

    private final JdbcTemplate jdbc;

    @Override
    public void record(String email, String ipAddress, String userAgent, boolean success, String reason, Instant at) {
        jdbc.update("INSERT INTO login_attempts (email, ip_address, user_agent, success, reason, attempted_at) VALUES (?, ?, ?, ?, ?, ?)",
                email, ipAddress, userAgent, success, reason, Timestamp.from(at));
    }

    @Override
    public long countFailuresFromIp(String ipAddress, Instant since) {
        Long count = jdbc.queryForObject(
                "SELECT count(*) FROM login_attempts WHERE ip_address = ? AND success = FALSE AND attempted_at > ?",
                Long.class, ipAddress, Timestamp.from(since));
        return count == null ? 0 : count;
    }

    @Override
    public List<String> recentHashes(long userId, int depth) {
        return jdbc.queryForList(
                "SELECT password_hash FROM user_password_history WHERE user_id = ? ORDER BY created_at DESC LIMIT ?",
                String.class, userId, depth);
    }

    @Override
    public void add(long userId, String passwordHash) {
        jdbc.update("INSERT INTO user_password_history (user_id, password_hash) VALUES (?, ?)", userId, passwordHash);
    }

    @Override
    public void replaceAll(long userId, List<String> codeHashes) {
        deleteAll(userId);
        jdbc.batchUpdate("INSERT INTO mfa_recovery_codes (user_id, code_hash) VALUES (?, ?)",
                codeHashes.stream().map(h -> new Object[]{userId, h}).toList());
    }

    @Override
    public boolean consume(long userId, String codeHash) {
        return jdbc.update("UPDATE mfa_recovery_codes SET used_at = now() WHERE user_id = ? AND code_hash = ? AND used_at IS NULL",
                userId, codeHash) == 1;
    }

    @Override
    public int remaining(long userId) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM mfa_recovery_codes WHERE user_id = ? AND used_at IS NULL",
                Integer.class, userId);
        return count == null ? 0 : count;
    }

    @Override
    public void deleteAll(long userId) {
        jdbc.update("DELETE FROM mfa_recovery_codes WHERE user_id = ?", userId);
    }
}
