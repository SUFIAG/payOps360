package com.payops.notification.adapter.out.persistence;

import com.payops.identity.application.port.out.SecretCipher;
import com.payops.notification.application.port.out.NotificationConfigRepository;
import com.payops.notification.domain.ChannelTarget;
import com.payops.notification.domain.ChannelType;
import com.payops.notification.domain.EscalationPolicy;
import com.payops.notification.domain.NotificationChannel;
import com.payops.notification.domain.NotificationRule;
import com.payops.shared.domain.Severity;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Plain-JDBC persistence of notification configuration. Channel targets and
 * signing secrets are encrypted at rest with the platform's AES-256-GCM
 * {@link SecretCipher}; only the masked display value is stored in clear.
 */
@Component
@RequiredArgsConstructor
class JdbcNotificationConfigRepository implements NotificationConfigRepository {

    private static final String CHANNEL_COLUMNS = "id, public_id, organization_id, name, type, enabled, target_display, "
            + "secret_ciphertext IS NOT NULL AS has_secret, created_by, created_at, updated_at";
    private static final String RULE_COLUMNS = "id, public_id, organization_id, name, event_type, min_severity, "
            + "provider_code, channel_ids, enabled, created_at, updated_at";

    private static final RowMapper<NotificationChannel> CHANNEL = (rs, i) -> new NotificationChannel(
            rs.getLong("id"), rs.getObject("public_id", UUID.class), rs.getLong("organization_id"), rs.getString("name"),
            ChannelType.valueOf(rs.getString("type")), rs.getBoolean("enabled"), rs.getString("target_display"),
            rs.getBoolean("has_secret"), rs.getString("created_by"), instant(rs, "created_at"),
            instant(rs, "updated_at"));

    private static final RowMapper<NotificationRule> RULE = (rs, i) -> new NotificationRule(
            rs.getLong("id"), rs.getObject("public_id", UUID.class), rs.getLong("organization_id"), rs.getString("name"),
            rs.getString("event_type"), Severity.valueOf(rs.getString("min_severity")), rs.getString("provider_code"),
            ids(rs.getArray("channel_ids")), rs.getBoolean("enabled"), instant(rs, "created_at"),
            instant(rs, "updated_at"));

    private final JdbcTemplate jdbc;
    private final SecretCipher cipher;

    // ------------------------------------------------------------ channels

    @Override
    public List<NotificationChannel> channels(long organizationId) {
        return jdbc.query("SELECT " + CHANNEL_COLUMNS + " FROM notification_channels WHERE organization_id = ? "
                + "ORDER BY lower(name)", CHANNEL, organizationId);
    }

    @Override
    public Optional<NotificationChannel> channel(long organizationId, UUID publicId) {
        return jdbc.query("SELECT " + CHANNEL_COLUMNS + " FROM notification_channels WHERE organization_id = ? "
                + "AND public_id = ?", CHANNEL, organizationId, publicId).stream().findFirst();
    }

    @Override
    public Map<Long, NotificationChannel> channelsById(long organizationId, Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return jdbc.query("SELECT " + CHANNEL_COLUMNS + " FROM notification_channels WHERE organization_id = ? "
                        + "AND id = ANY (CAST(? AS BIGINT[]))", CHANNEL, organizationId, arrayLiteral(ids)).stream()
                .collect(Collectors.toMap(NotificationChannel::id, Function.identity()));
    }

    @Override
    public ChannelTarget target(long channelId) {
        return jdbc.queryForObject("SELECT target_ciphertext, secret_ciphertext FROM notification_channels WHERE id = ?",
                (rs, i) -> new ChannelTarget(cipher.decrypt(rs.getString("target_ciphertext")),
                        rs.getString("secret_ciphertext") == null ? null : cipher.decrypt(rs.getString("secret_ciphertext"))),
                channelId);
    }

    @Override
    public NotificationChannel insertChannel(long organizationId, String name, ChannelType type, boolean enabled,
                                             ChannelTarget target, String actor, Instant now) {
        UUID publicId = UUID.randomUUID();
        jdbc.update("INSERT INTO notification_channels (public_id, organization_id, name, type, enabled, target_ciphertext, "
                        + "secret_ciphertext, target_display, created_by, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                publicId, organizationId, name, type.name(), enabled, cipher.encrypt(target.target()),
                target.signingSecret() == null ? null : cipher.encrypt(target.signingSecret()), target.display(type),
                actor, Timestamp.from(now), Timestamp.from(now));
        return channel(organizationId, publicId).orElseThrow();
    }

    @Override
    public NotificationChannel updateChannel(NotificationChannel channel, String name, boolean enabled,
                                             ChannelTarget target, Instant now) {
        if (target == null) {
            jdbc.update("UPDATE notification_channels SET name = ?, enabled = ?, updated_at = ? WHERE id = ?",
                    name, enabled, Timestamp.from(now), channel.id());
        } else {
            jdbc.update("UPDATE notification_channels SET name = ?, enabled = ?, target_ciphertext = ?, "
                            + "secret_ciphertext = ?, target_display = ?, updated_at = ? WHERE id = ?",
                    name, enabled, cipher.encrypt(target.target()),
                    target.signingSecret() == null ? null : cipher.encrypt(target.signingSecret()),
                    target.display(channel.type()), Timestamp.from(now), channel.id());
        }
        return channel(channel.organizationId(), channel.publicId()).orElseThrow();
    }

    @Override
    public void deleteChannel(long organizationId, long channelId) {
        jdbc.update("UPDATE notification_rules SET channel_ids = array_remove(channel_ids, ?) "
                + "WHERE organization_id = ? AND channel_ids @> ARRAY[CAST(? AS BIGINT)]", channelId, organizationId, channelId);
        jdbc.update("UPDATE escalation_levels l SET channel_ids = array_remove(l.channel_ids, ?) FROM escalation_policies p "
                        + "WHERE l.policy_id = p.id AND p.organization_id = ? AND l.channel_ids @> ARRAY[CAST(? AS BIGINT)]",
                channelId, organizationId, channelId);
        jdbc.update("DELETE FROM notification_channels WHERE id = ? AND organization_id = ?", channelId, organizationId);
    }

    // --------------------------------------------------------------- rules

    @Override
    public List<NotificationRule> rules(long organizationId) {
        return jdbc.query("SELECT " + RULE_COLUMNS + " FROM notification_rules WHERE organization_id = ? "
                + "ORDER BY event_type, lower(name)", RULE, organizationId);
    }

    @Override
    public List<NotificationRule> enabledRules(long organizationId, String eventType) {
        return jdbc.query("SELECT " + RULE_COLUMNS + " FROM notification_rules WHERE organization_id = ? "
                + "AND event_type = ? AND enabled", RULE, organizationId, eventType);
    }

    @Override
    public Optional<NotificationRule> rule(long organizationId, UUID publicId) {
        return jdbc.query("SELECT " + RULE_COLUMNS + " FROM notification_rules WHERE organization_id = ? AND public_id = ?",
                RULE, organizationId, publicId).stream().findFirst();
    }

    @Override
    public NotificationRule saveRule(NotificationRule r, Instant now) {
        if (r.id() == 0) {
            jdbc.update("INSERT INTO notification_rules (public_id, organization_id, name, event_type, min_severity, "
                            + "provider_code, channel_ids, enabled, created_at, updated_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, CAST(? AS BIGINT[]), ?, ?, ?)",
                    r.publicId(), r.organizationId(), r.name(), r.eventType(), r.minSeverity().name(), r.providerCode(),
                    arrayLiteral(r.channelIds()), r.enabled(), Timestamp.from(now), Timestamp.from(now));
        } else {
            jdbc.update("UPDATE notification_rules SET name = ?, event_type = ?, min_severity = ?, provider_code = ?, "
                            + "channel_ids = CAST(? AS BIGINT[]), enabled = ?, updated_at = ? WHERE id = ? AND organization_id = ?",
                    r.name(), r.eventType(), r.minSeverity().name(), r.providerCode(), arrayLiteral(r.channelIds()),
                    r.enabled(), Timestamp.from(now), r.id(), r.organizationId());
        }
        return rule(r.organizationId(), r.publicId()).orElseThrow();
    }

    @Override
    public void deleteRule(long organizationId, long ruleId) {
        jdbc.update("DELETE FROM notification_rules WHERE id = ? AND organization_id = ?", ruleId, organizationId);
    }

    // ---------------------------------------------------------- escalation

    @Override
    public List<EscalationPolicy> policies(long organizationId) {
        return loadPolicies("WHERE organization_id = ? ORDER BY lower(name)", organizationId);
    }

    @Override
    public List<EscalationPolicy> enabledPolicies() {
        return loadPolicies("WHERE enabled");
    }

    @Override
    public Optional<EscalationPolicy> policy(long organizationId, UUID publicId) {
        return loadPolicies("WHERE organization_id = ? AND public_id = ?", organizationId, publicId).stream().findFirst();
    }

    @Override
    public EscalationPolicy savePolicy(EscalationPolicy p, Instant now) {
        long id;
        if (p.id() == 0) {
            id = jdbc.queryForObject("INSERT INTO escalation_policies (public_id, organization_id, name, min_severity, "
                            + "enabled, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id", Long.class,
                    p.publicId(), p.organizationId(), p.name(), p.minSeverity().name(), p.enabled(), Timestamp.from(now),
                    Timestamp.from(now));
        } else {
            id = p.id();
            jdbc.update("UPDATE escalation_policies SET name = ?, min_severity = ?, enabled = ?, updated_at = ? "
                            + "WHERE id = ? AND organization_id = ?", p.name(), p.minSeverity().name(), p.enabled(),
                    Timestamp.from(now), id, p.organizationId());
            jdbc.update("DELETE FROM escalation_levels WHERE policy_id = ?", id);
        }
        jdbc.batchUpdate("INSERT INTO escalation_levels (policy_id, level_no, delay_minutes, channel_ids) "
                        + "VALUES (?, ?, ?, CAST(? AS BIGINT[]))",
                p.levels().stream().map(l -> new Object[]{id, l.number(), l.delayMinutes(), arrayLiteral(l.channelIds())})
                        .toList());
        return policy(p.organizationId(), p.publicId()).orElseThrow();
    }

    @Override
    public void deletePolicy(long organizationId, long policyId) {
        jdbc.update("DELETE FROM escalation_policies WHERE id = ? AND organization_id = ?", policyId, organizationId);
    }

    private List<EscalationPolicy> loadPolicies(String where, Object... args) {
        record Row(long id, UUID publicId, long organizationId, String name, Severity minSeverity, boolean enabled,
                   Instant createdAt, Instant updatedAt) {
        }
        List<Row> rows = jdbc.query("SELECT id, public_id, organization_id, name, min_severity, enabled, created_at, "
                        + "updated_at FROM escalation_policies " + where,
                (rs, i) -> new Row(rs.getLong("id"), rs.getObject("public_id", UUID.class), rs.getLong("organization_id"),
                        rs.getString("name"), Severity.valueOf(rs.getString("min_severity")), rs.getBoolean("enabled"),
                        instant(rs, "created_at"), instant(rs, "updated_at")), args);
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, List<EscalationPolicy.Level>> levels = new HashMap<>();
        jdbc.query("SELECT policy_id, level_no, delay_minutes, channel_ids FROM escalation_levels "
                        + "WHERE policy_id = ANY (CAST(? AS BIGINT[])) ORDER BY policy_id, level_no",
                rs -> {
                    levels.computeIfAbsent(rs.getLong("policy_id"), k -> new ArrayList<>())
                            .add(new EscalationPolicy.Level(rs.getInt("level_no"), rs.getInt("delay_minutes"),
                                    ids(rs.getArray("channel_ids"))));
                }, arrayLiteral(rows.stream().map(Row::id).toList()));
        Map<Long, EscalationPolicy> result = new LinkedHashMap<>();
        for (Row r : rows) {
            result.put(r.id(), new EscalationPolicy(r.id(), r.publicId(), r.organizationId(), r.name(), r.minSeverity(),
                    r.enabled(), levels.getOrDefault(r.id(), List.of()), r.createdAt(), r.updatedAt()));
        }
        return new ArrayList<>(result.values());
    }

    // ------------------------------------------------------------------------

    private static String arrayLiteral(Collection<Long> ids) {
        return ids.stream().map(String::valueOf).collect(Collectors.joining(",", "{", "}"));
    }

    private static List<Long> ids(Array array) throws SQLException {
        if (array == null) {
            return List.of();
        }
        Object[] values = (Object[]) array.getArray();
        return Arrays.stream(values).map(v -> ((Number) v).longValue()).toList();
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
