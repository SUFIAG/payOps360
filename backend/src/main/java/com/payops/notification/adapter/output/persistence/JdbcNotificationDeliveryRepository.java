package com.payops.notification.adapter.output.persistence;

import com.payops.notification.application.port.output.NotificationDeliveryRepository;
import com.payops.notification.domain.ChannelType;
import com.payops.notification.domain.NotificationChannel;
import com.payops.notification.domain.NotificationDelivery;
import com.payops.notification.domain.NotificationMessage;
import com.payops.shared.domain.Severity;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Plain-JDBC delivery outbox. Uses the caller's connection, so rows queued by
 * routing take part in the surrounding transaction.
 */
@Component
@RequiredArgsConstructor
class JdbcNotificationDeliveryRepository implements NotificationDeliveryRepository {

    private static final int MAX_TITLE = 300;
    private static final String COLUMNS = "id, public_id, organization_id, channel_id, channel_name, channel_type, rule_id, "
            + "source, event_type, event_key, severity, subject_type, subject_reference, title, body, link, status, "
            + "attempts, last_error, next_attempt_at, created_at, sent_at";
    private static final RowMapper<NotificationDelivery> MAPPER = (rs, i) -> new NotificationDelivery(
            rs.getLong("id"), rs.getObject("public_id", UUID.class), rs.getLong("organization_id"),
            (Long) rs.getObject("channel_id"), rs.getString("channel_name"), ChannelType.valueOf(rs.getString("channel_type")),
            (Long) rs.getObject("rule_id"), NotificationDelivery.Source.valueOf(rs.getString("source")),
            rs.getString("event_key"),
            new NotificationMessage(rs.getString("event_type"),
                    rs.getString("severity") == null ? null : Severity.valueOf(rs.getString("severity")),
                    rs.getString("subject_type"), rs.getString("subject_reference"), rs.getString("title"),
                    rs.getString("body"), rs.getString("link"), instant(rs, "created_at")),
            NotificationDelivery.Status.valueOf(rs.getString("status")), rs.getInt("attempts"),
            rs.getString("last_error"), instant(rs, "next_attempt_at"), instant(rs, "created_at"),
            instant(rs, "sent_at"));

    private final JdbcTemplate jdbc;

    @Override
    public NotificationDelivery enqueue(NotificationChannel channel, Long ruleId, NotificationDelivery.Source source,
                                        String eventKey, NotificationMessage message, Instant now) {
        return insert(channel, ruleId, source, eventKey, message, "PENDING", 0, null, now, null);
    }

    @Override
    public NotificationDelivery recordAttempt(NotificationChannel channel, NotificationDelivery.Source source,
                                              NotificationMessage message, boolean success, String error, Instant now) {
        return insert(channel, null, source, null, message, success ? "SENT" : "FAILED", 1, error, now,
                success ? now : null);
    }

    private NotificationDelivery insert(NotificationChannel channel, Long ruleId, NotificationDelivery.Source source,
                                        String eventKey, NotificationMessage m, String status, int attempts,
                                        String error, Instant now, Instant sentAt) {
        Long id = jdbc.queryForObject("INSERT INTO notification_deliveries (public_id, organization_id, channel_id, "
                        + "channel_name, channel_type, rule_id, source, event_type, event_key, severity, subject_type, "
                        + "subject_reference, title, body, link, status, attempts, last_error, next_attempt_at, created_at, "
                        + "sent_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id",
                Long.class, UUID.randomUUID(), channel.organizationId(), channel.id(), channel.name(),
                channel.type().name(), ruleId, source.name(), m.eventType(), truncate(eventKey, 200),
                m.severity() == null ? null : m.severity().name(), m.subjectType(), m.subjectReference(),
                truncate(m.title(), MAX_TITLE), m.body(), truncate(m.link(), 500), status, attempts,
                truncate(error, 1000), Timestamp.from(now), Timestamp.from(now),
                sentAt == null ? null : Timestamp.from(sentAt));
        return find(id).orElseThrow();
    }

    @Override
    public List<NotificationDelivery> findDue(Instant now, int limit) {
        return jdbc.query("SELECT " + COLUMNS + " FROM notification_deliveries WHERE status = 'PENDING' "
                + "AND next_attempt_at <= ? ORDER BY next_attempt_at, id LIMIT ?", MAPPER, Timestamp.from(now), limit);
    }

    @Override
    public void markSent(long id, Instant at) {
        jdbc.update("UPDATE notification_deliveries SET status = 'SENT', sent_at = ?, attempts = attempts + 1, "
                + "last_error = NULL WHERE id = ?", Timestamp.from(at), id);
    }

    @Override
    public void markAttemptFailed(long id, String error, Instant nextAttemptAt, boolean terminal) {
        jdbc.update("UPDATE notification_deliveries SET attempts = attempts + 1, last_error = ?, next_attempt_at = ?, "
                + "status = ? WHERE id = ?", truncate(error, 1000), Timestamp.from(nextAttemptAt),
                terminal ? "FAILED" : "PENDING", id);
    }

    @Override
    public List<NotificationDelivery> recent(long organizationId, NotificationDelivery.Status status, int limit) {
        if (status == null) {
            return jdbc.query("SELECT " + COLUMNS + " FROM notification_deliveries WHERE organization_id = ? "
                    + "ORDER BY created_at DESC, id DESC LIMIT ?", MAPPER, organizationId, limit);
        }
        return jdbc.query("SELECT " + COLUMNS + " FROM notification_deliveries WHERE organization_id = ? AND status = ? "
                + "ORDER BY created_at DESC, id DESC LIMIT ?", MAPPER, organizationId, status.name(), limit);
    }

    @Override
    public Optional<NotificationDelivery> find(long id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM notification_deliveries WHERE id = ?", MAPPER, id).stream()
                .findFirst();
    }

    @Override
    public boolean claimDedup(long ruleId, String eventKey, Instant now, Duration window) {
        // Inserts the marker, or refreshes it only when the previous one is outside the window.
        // Exactly one concurrent caller wins thanks to the primary key.
        int claimed = jdbc.update("""
                INSERT INTO notification_dedup (rule_id, event_key, last_sent_at) VALUES (?, ?, ?)
                ON CONFLICT (rule_id, event_key) DO UPDATE SET last_sent_at = EXCLUDED.last_sent_at
                WHERE notification_dedup.last_sent_at <= ?
                """, ruleId, truncate(eventKey, 200), Timestamp.from(now), Timestamp.from(now.minus(window)));
        return claimed > 0;
    }

    @Override
    public int purgeDedup(Instant before) {
        return jdbc.update("DELETE FROM notification_dedup WHERE last_sent_at < ?", Timestamp.from(before));
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
