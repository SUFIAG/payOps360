package com.payops.notification.adapter.output.persistence;

import com.payops.notification.application.port.output.EmailOutboxRepository;
import com.payops.notification.domain.EmailMessage;
import com.payops.notification.domain.EmailStatus;
import com.payops.notification.domain.OutboxEmail;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Plain-JDBC outbox. Uses the caller's connection, so inserts take part in
 * the surrounding JPA transaction.
 */
@Component
@RequiredArgsConstructor
class JdbcEmailOutboxRepository implements EmailOutboxRepository {

    private static final String COLUMNS = "id, public_id, organization_id, recipient, subject, category, text_body, "
            + "html_body, status, attempts, last_error, created_at, sent_at";
    private static final RowMapper<OutboxEmail> MAPPER = (rs, i) -> new OutboxEmail(
            rs.getLong("id"),
            rs.getObject("public_id", UUID.class),
            (Long) rs.getObject("organization_id"),
            rs.getString("recipient"),
            rs.getString("subject"),
            rs.getString("category"),
            rs.getString("text_body"),
            rs.getString("html_body"),
            EmailStatus.valueOf(rs.getString("status")),
            rs.getInt("attempts"),
            rs.getString("last_error"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("sent_at") == null ? null : rs.getTimestamp("sent_at").toInstant());

    private final JdbcTemplate jdbc;

    @Override
    public void enqueue(EmailMessage message, Instant now) {
        jdbc.update("INSERT INTO email_outbox (public_id, organization_id, recipient, subject, category, text_body, "
                        + "html_body, status, next_attempt_at, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, 'PENDING', ?, ?)",
                UUID.randomUUID(), message.organizationId(), message.recipient(), message.subject(),
                message.category(), message.content().text(), message.content().html(), Timestamp.from(now),
                Timestamp.from(now));
    }

    @Override
    public List<OutboxEmail> findDue(Instant now, int limit) {
        return jdbc.query("SELECT " + COLUMNS + " FROM email_outbox WHERE status = 'PENDING' AND next_attempt_at <= ? "
                + "ORDER BY next_attempt_at, id LIMIT ?", MAPPER, Timestamp.from(now), limit);
    }

    @Override
    public void markDelivered(long id, EmailStatus status, Instant at, boolean redactBodies) {
        jdbc.update("UPDATE email_outbox SET status = ?, sent_at = ?, attempts = attempts + 1, last_error = NULL"
                        + (redactBodies ? ", text_body = NULL, html_body = NULL" : "") + " WHERE id = ?",
                status.name(), Timestamp.from(at), id);
    }

    @Override
    public void markAttemptFailed(long id, String error, Instant nextAttemptAt, boolean terminal) {
        jdbc.update("UPDATE email_outbox SET attempts = attempts + 1, last_error = ?, next_attempt_at = ?, status = ? "
                + "WHERE id = ?", error, Timestamp.from(nextAttemptAt), terminal ? "FAILED" : "PENDING", id);
    }

    @Override
    public List<OutboxEmail> recent(int limit) {
        return jdbc.query("SELECT " + COLUMNS + " FROM email_outbox ORDER BY created_at DESC, id DESC LIMIT ?",
                MAPPER, limit);
    }
}
