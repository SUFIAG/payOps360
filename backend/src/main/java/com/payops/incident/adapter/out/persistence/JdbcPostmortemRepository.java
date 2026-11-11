package com.payops.incident.adapter.out.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payops.incident.application.port.out.PostmortemRepository;
import com.payops.incident.domain.Postmortem;
import com.payops.shared.domain.ConflictException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Postmortems with the timeline and action items stored as JSONB documents.
 */
@Component
@RequiredArgsConstructor
class JdbcPostmortemRepository implements PostmortemRepository {

    private static final TypeReference<List<Postmortem.TimelineItem>> TIMELINE = new TypeReference<>() {
    };
    private static final TypeReference<List<Postmortem.ActionItem>> ACTION_ITEMS = new TypeReference<>() {
    };
    private static final String COLUMNS = "id, organization_id, incident_id, status, summary, impact, root_cause, "
            + "timeline::text AS timeline, action_items::text AS action_items, ai_engine, created_by, updated_by, "
            + "published_by, created_at, updated_at, published_at";

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    @Override
    public Optional<Postmortem> findByIncident(long organizationId, long incidentId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM incident_postmortems WHERE organization_id = ? AND incident_id = ?",
                mapper(), organizationId, incidentId).stream().findFirst();
    }

    @Override
    public Postmortem save(Postmortem p) {
        String timeline = toJson(p.timeline());
        String items = toJson(p.actionItems());
        if (p.id() == null) {
            List<Long> created = jdbc.queryForList("""
                            INSERT INTO incident_postmortems (incident_id, organization_id, status, summary, impact, root_cause,
                                timeline, action_items, ai_engine, created_by, updated_by, published_by, created_at,
                                updated_at, published_at)
                            VALUES (?, ?, ?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb), ?, ?, ?, ?, ?, ?, ?)
                            ON CONFLICT (incident_id) DO NOTHING
                            RETURNING id
                            """, Long.class, p.incidentId(), p.organizationId(), p.status().name(), p.summary(),
                    p.impact(), p.rootCause(), timeline, items, p.aiEngine(), p.createdBy(), p.updatedBy(),
                    p.publishedBy(), ts(p.createdAt()), ts(p.updatedAt()), ts(p.publishedAt()));
            if (created.isEmpty()) {
                throw new ConflictException("POSTMORTEM_EXISTS", "The postmortem was created concurrently; reload and retry");
            }
        } else {
            jdbc.update("""
                            UPDATE incident_postmortems SET status = ?, summary = ?, impact = ?, root_cause = ?,
                                timeline = CAST(? AS jsonb), action_items = CAST(? AS jsonb), ai_engine = ?, updated_by = ?,
                                published_by = ?, updated_at = ?, published_at = ?, version = version + 1
                            WHERE id = ? AND organization_id = ?
                            """, p.status().name(), p.summary(), p.impact(), p.rootCause(), timeline, items,
                    p.aiEngine(), p.updatedBy(), p.publishedBy(), ts(p.updatedAt()), ts(p.publishedAt()), p.id(),
                    p.organizationId());
        }
        return findByIncident(p.organizationId(), p.incidentId()).orElseThrow();
    }

    private RowMapper<Postmortem> mapper() {
        return (rs, i) -> new Postmortem(rs.getLong("id"), rs.getLong("organization_id"), rs.getLong("incident_id"),
                Postmortem.Status.valueOf(rs.getString("status")), rs.getString("summary"), rs.getString("impact"),
                rs.getString("root_cause"), fromJson(rs.getString("timeline"), TIMELINE),
                fromJson(rs.getString("action_items"), ACTION_ITEMS), rs.getString("ai_engine"),
                rs.getString("created_by"), rs.getString("updated_by"), rs.getString("published_by"),
                instant(rs, "created_at"), instant(rs, "updated_at"), instant(rs, "published_at"));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise postmortem", e);
        }
    }

    private <T> T fromJson(String json, TypeReference<T> type) {
        try {
            return objectMapper.readValue(json == null ? "[]" : json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Corrupt postmortem document", e);
        }
    }

    private static Timestamp ts(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
