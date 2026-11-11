package com.payops.notification.adapter.out.persistence;

import com.payops.notification.application.port.out.EscalationProgressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

@Component
@RequiredArgsConstructor
class JdbcEscalationProgressRepository implements EscalationProgressRepository {

    private final JdbcTemplate jdbc;

    @Override
    public Optional<Progress> find(long organizationId, String subjectType, String subjectReference) {
        return jdbc.query("SELECT policy_id, level, last_escalated_at FROM escalation_progress "
                        + "WHERE organization_id = ? AND subject_type = ? AND subject_reference = ?",
                (rs, i) -> new Progress(rs.getLong("policy_id"), rs.getInt("level"),
                        rs.getTimestamp("last_escalated_at").toInstant()),
                organizationId, subjectType, subjectReference).stream().findFirst();
    }

    @Override
    public void save(long organizationId, String subjectType, String subjectReference, long policyId, int level,
                     Instant at) {
        jdbc.update("""
                INSERT INTO escalation_progress (organization_id, subject_type, subject_reference, policy_id, level,
                    last_escalated_at, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (organization_id, subject_type, subject_reference) DO UPDATE
                    SET policy_id = EXCLUDED.policy_id, level = EXCLUDED.level, last_escalated_at = EXCLUDED.last_escalated_at
                """, organizationId, subjectType, subjectReference, policyId, level, Timestamp.from(at),
                Timestamp.from(at));
    }
}
