package com.payops.incident.adapter.output.persistence;

import com.payops.incident.application.port.output.SlaPolicyRepository;
import com.payops.incident.domain.SlaPolicy;
import com.payops.shared.domain.Severity;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
class JdbcSlaPolicyRepository implements SlaPolicyRepository {

    private final JdbcTemplate jdbc;

    @Override
    public Map<Severity, SlaPolicy.Target> overrides(long organizationId) {
        Map<Severity, SlaPolicy.Target> targets = new EnumMap<>(Severity.class);
        jdbc.query("SELECT severity, ack_minutes, resolve_minutes FROM incident_sla_policies WHERE organization_id = ?",
                rs -> {
                    targets.put(Severity.valueOf(rs.getString("severity")),
                            new SlaPolicy.Target(rs.getInt("ack_minutes"), rs.getInt("resolve_minutes")));
                }, organizationId);
        return targets;
    }

    @Override
    public void save(long organizationId, Map<Severity, SlaPolicy.Target> targets, String actor, Instant now) {
        jdbc.batchUpdate("""
                        INSERT INTO incident_sla_policies (organization_id, severity, ack_minutes, resolve_minutes, updated_by, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?)
                        ON CONFLICT (organization_id, severity) DO UPDATE SET ack_minutes = EXCLUDED.ack_minutes,
                            resolve_minutes = EXCLUDED.resolve_minutes, updated_by = EXCLUDED.updated_by,
                            updated_at = EXCLUDED.updated_at
                        """,
                targets.entrySet().stream().map(e -> new Object[]{organizationId, e.getKey().name(),
                        e.getValue().ackMinutes(), e.getValue().resolveMinutes(), actor, Timestamp.from(now)}).toList());
    }
}
