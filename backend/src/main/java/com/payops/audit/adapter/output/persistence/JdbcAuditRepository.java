package com.payops.audit.adapter.output.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payops.audit.application.port.output.AuditRepository;
import com.payops.audit.domain.AuditAction;
import com.payops.audit.domain.AuditEntry;
import com.payops.audit.domain.AuditOutcome;
import com.payops.audit.domain.StoredAuditEntry;
import com.payops.shared.domain.PageQuery;
import com.payops.shared.domain.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

/**
 * Append-only JDBC adapter. A transaction-scoped advisory lock per tenant
 * serialises appends so the hash chain cannot fork under concurrency.
 */
@Repository
@RequiredArgsConstructor
public class JdbcAuditRepository implements AuditRepository {

    private static final long GLOBAL_CHAIN = 0L;
    private static final TypeReference<Map<String, String>> MAP_TYPE = new TypeReference<>() {
    };

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    @Override
    public void appendChained(AuditEntry entry) {
        long chainKey = entry.organizationId() == null ? GLOBAL_CHAIN : entry.organizationId();
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(?, ?)", Object.class, 360360, (int) chainKey);
        List<String> previous = jdbc.queryForList(
                "SELECT entry_hash FROM audit_logs WHERE organization_id IS NOT DISTINCT FROM ? ORDER BY id DESC LIMIT 1",
                String.class, entry.organizationId());
        String previousHash = previous.isEmpty() ? null : previous.get(0);
        jdbc.update("""
                INSERT INTO audit_logs (organization_id, actor_user_id, actor_email, action, resource_type, resource_id,
                                        outcome, ip_address, user_agent, details, previous_hash, entry_hash, occurred_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?, ?)
                """,
                entry.organizationId(), entry.actorUserId(), entry.actorEmail(), entry.action().name(),
                entry.resourceType(), entry.resourceId(), entry.outcome().name(), entry.ipAddress(),
                entry.userAgent(), toJson(entry.details()), previousHash, entry.chainHash(previousHash),
                Timestamp.from(entry.occurredAt()));
    }

    @Override
    public PageResult<StoredAuditEntry> search(long organizationId, String action, PageQuery page) {
        String filter = action == null ? "" : " AND action = ?";
        Object[] countArgs = action == null ? new Object[]{organizationId} : new Object[]{organizationId, action};
        Long total = jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE organization_id = ?" + filter,
                Long.class, countArgs);
        Object[] args = action == null
                ? new Object[]{organizationId, page.size(), (long) page.page() * page.size()}
                : new Object[]{organizationId, action, page.size(), (long) page.page() * page.size()};
        List<StoredAuditEntry> items = jdbc.query(
                "SELECT * FROM audit_logs WHERE organization_id = ?" + filter + " ORDER BY id DESC LIMIT ? OFFSET ?",
                rowMapper(), args);
        return new PageResult<>(items, page.page(), page.size(), total == null ? 0 : total);
    }

    @Override
    public List<StoredAuditEntry> chronological(long organizationId, long afterId, int limit) {
        return jdbc.query("SELECT * FROM audit_logs WHERE organization_id = ? AND id > ? ORDER BY id LIMIT ?",
                rowMapper(), organizationId, afterId, limit);
    }

    private RowMapper<StoredAuditEntry> rowMapper() {
        return (rs, rowNum) -> new StoredAuditEntry(
                rs.getLong("id"),
                new AuditEntry(
                        (Long) rs.getObject("organization_id"),
                        (Long) rs.getObject("actor_user_id"),
                        rs.getString("actor_email"),
                        AuditAction.valueOf(rs.getString("action")),
                        rs.getString("resource_type"),
                        rs.getString("resource_id"),
                        AuditOutcome.valueOf(rs.getString("outcome")),
                        rs.getString("ip_address"),
                        rs.getString("user_agent"),
                        fromJson(rs.getString("details")),
                        rs.getTimestamp("occurred_at").toInstant()),
                rs.getString("previous_hash"),
                rs.getString("entry_hash"));
    }

    private String toJson(Map<String, String> details) {
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private Map<String, String> fromJson(String json) {
        if (json == null) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
