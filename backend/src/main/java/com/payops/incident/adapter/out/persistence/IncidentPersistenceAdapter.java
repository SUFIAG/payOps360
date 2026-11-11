package com.payops.incident.adapter.out.persistence;

import com.payops.incident.application.port.out.IncidentRepository;
import com.payops.incident.domain.AiAnalysis;
import com.payops.incident.domain.Incident;
import com.payops.incident.domain.IncidentStatus;
import com.payops.incident.domain.SlaClock;
import com.payops.incident.domain.TimelineEntry;
import com.payops.shared.domain.PageQuery;
import com.payops.shared.domain.PageResult;
import com.payops.shared.domain.Severity;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
class IncidentPersistenceAdapter implements IncidentRepository {

    private final IncidentJpaRepository jpa;
    private final JdbcTemplate jdbc;

    @Override
    public Incident save(Incident incident) {
        IncidentEntity e = incident.id() == null ? new IncidentEntity() : jpa.findById(incident.id()).orElseThrow();
        Instant now = Instant.now();
        if (e.getId() == null) {
            e.setOrganizationId(incident.organizationId());
            e.setReference(incident.reference());
            e.setTitle(incident.title());
            e.setProviderId(incident.providerId());
            e.setOpenedAt(incident.openedAt());
            e.setCreatedAt(now);
        }
        e.setDescription(incident.description());
        e.setSeverity(incident.severity().name());
        e.setStatus(incident.status().name());
        e.setAffectedPayments(incident.affectedPayments());
        e.setAffectedAmount(incident.affectedAmount());
        e.setAcknowledgedAt(incident.acknowledgedAt());
        e.setAcknowledgedBy(incident.acknowledgedBy());
        e.setResolvedAt(incident.resolvedAt());
        e.setResolvedBy(incident.resolvedBy());
        e.setResolutionNote(incident.resolutionNote());
        AiAnalysis ai = incident.aiAnalysis();
        if (ai != null) {
            e.setAiSummary(ai.summary());
            e.setAiRootCause(ai.rootCause());
            e.setAiRecommendations(new ArrayList<>(ai.recommendations()));
            e.setAiConfidence(ai.confidence());
            e.setAiEngine(ai.engine());
            e.setAiGeneratedAt(ai.generatedAt());
        }
        SlaClock sla = incident.sla();
        e.setAckDueAt(sla.ackDueAt());
        e.setResolveDueAt(sla.resolveDueAt());
        e.setAckBreachedAt(sla.ackBreachedAt());
        e.setResolveBreachedAt(sla.resolveBreachedAt());
        e.setEscalationLevel(incident.escalationLevel());
        e.setUpdatedAt(now);
        IncidentEntity saved = jpa.saveAndFlush(e);
        jdbc.batchUpdate("INSERT INTO incident_timeline (incident_id, kind, message, actor, occurred_at) VALUES (?, ?, ?, ?, ?)",
                incident.pendingTimeline().stream().map(t -> new Object[]{saved.getId(), t.kind().name(),
                        truncate(t.message()), t.actor(), Timestamp.from(t.occurredAt())}).toList());
        incident.clearPendingTimeline();
        return toDomain(saved);
    }

    @Override
    public Optional<Incident> findByReference(long organizationId, String reference) {
        return jpa.findByOrganizationIdAndReference(organizationId, reference).map(IncidentPersistenceAdapter::toDomain);
    }

    @Override
    public Optional<Incident> findById(long organizationId, long id) {
        return jpa.findByOrganizationIdAndId(organizationId, id).map(IncidentPersistenceAdapter::toDomain);
    }

    @Override
    public Optional<Incident> findActiveForProvider(long organizationId, Long providerId, Instant openedAfter) {
        return jpa.findActiveForProvider(organizationId, providerId, openedAfter, PageRequest.of(0, 1)).stream()
                .findFirst().map(IncidentPersistenceAdapter::toDomain);
    }

    @Override
    public PageResult<Incident> search(long organizationId, IncidentStatus status, PageQuery page) {
        Page<IncidentEntity> result = jpa.search(organizationId, status == null ? null : status.name(),
                PageRequest.of(page.page(), page.size()));
        return new PageResult<>(result.getContent().stream().map(IncidentPersistenceAdapter::toDomain).toList(),
                page.page(), page.size(), result.getTotalElements());
    }

    @Override
    public List<Incident> findActive(long organizationId) {
        return jpa.findByOrganizationIdAndStatusNot(organizationId, IncidentStatus.RESOLVED.name()).stream()
                .map(IncidentPersistenceAdapter::toDomain).toList();
    }

    @Override
    public List<TimelineEntry> timeline(long incidentId) {
        return jdbc.query("SELECT kind, message, actor, occurred_at FROM incident_timeline WHERE incident_id = ? ORDER BY occurred_at, id",
                (rs, i) -> new TimelineEntry(TimelineEntry.Kind.valueOf(rs.getString("kind")), rs.getString("message"),
                        rs.getString("actor"), rs.getTimestamp("occurred_at").toInstant()), incidentId);
    }

    @Override
    public Map<IncidentStatus, Long> countByStatus(long organizationId) {
        Map<IncidentStatus, Long> counts = new EnumMap<>(IncidentStatus.class);
        jpa.countByStatus(organizationId).forEach(r -> counts.put(IncidentStatus.valueOf((String) r[0]), (Long) r[1]));
        return counts;
    }

    @Override
    public List<Incident> findResolvedSince(long organizationId, Instant since) {
        return jpa.findByOrganizationIdAndStatusAndResolvedAtAfter(organizationId, IncidentStatus.RESOLVED.name(), since)
                .stream().map(IncidentPersistenceAdapter::toDomain).toList();
    }

    @Override
    public List<Incident> findSlaDue(Instant now, int limit) {
        // Two index-backed branches (see ix_incidents_ack_due / ix_incidents_resolve_due).
        List<Long> ids = jdbc.queryForList("""
                SELECT id FROM incidents WHERE status = 'OPEN' AND ack_breached_at IS NULL AND ack_due_at <= ?
                UNION
                SELECT id FROM incidents WHERE status <> 'RESOLVED' AND resolve_breached_at IS NULL AND resolve_due_at <= ?
                LIMIT ?
                """, Long.class, Timestamp.from(now), Timestamp.from(now), limit);
        return ids.isEmpty() ? List.of()
                : jpa.findAllById(ids).stream().map(IncidentPersistenceAdapter::toDomain).toList();
    }

    @Override
    public List<Incident> findAwaitingAcknowledgement(int limit) {
        return jpa.findAwaitingAcknowledgement(PageRequest.of(0, limit)).stream()
                .map(IncidentPersistenceAdapter::toDomain).toList();
    }

    private static String truncate(String message) {
        return message.length() <= 2000 ? message : message.substring(0, 1997) + "...";
    }

    private static Incident toDomain(IncidentEntity e) {
        AiAnalysis ai = e.getAiGeneratedAt() == null ? null : new AiAnalysis(e.getAiSummary(), e.getAiRootCause(),
                e.getAiRecommendations(), e.getAiConfidence(), e.getAiEngine(), e.getAiGeneratedAt());
        return new Incident(e.getId(), e.getOrganizationId(), e.getReference(), e.getTitle(), e.getDescription(),
                Severity.valueOf(e.getSeverity()), IncidentStatus.valueOf(e.getStatus()), e.getProviderId(),
                e.getAffectedPayments(), e.getAffectedAmount(), e.getOpenedAt(), e.getAcknowledgedAt(),
                e.getAcknowledgedBy(), e.getResolvedAt(), e.getResolvedBy(), e.getResolutionNote(), ai,
                new SlaClock(e.getAckDueAt(), e.getResolveDueAt(), e.getAckBreachedAt(), e.getResolveBreachedAt()),
                e.getEscalationLevel());
    }
}
