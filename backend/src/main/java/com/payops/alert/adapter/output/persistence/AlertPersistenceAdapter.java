package com.payops.alert.adapter.output.persistence;

import com.payops.alert.application.port.output.AlertRepository;
import com.payops.alert.domain.Alert;
import com.payops.alert.domain.AlertSource;
import com.payops.alert.domain.AlertStatus;
import com.payops.alert.domain.AlertType;
import com.payops.shared.domain.PageQuery;
import com.payops.shared.domain.PageResult;
import com.payops.shared.domain.Severity;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
class AlertPersistenceAdapter implements AlertRepository {

    private final AlertJpaRepository jpa;

    @Override
    public Alert save(Alert alert) {
        AlertEntity e = alert.id() == null ? new AlertEntity() : jpa.findById(alert.id()).orElseThrow();
        Instant now = Instant.now();
        if (e.getId() == null) {
            e.setOrganizationId(alert.organizationId());
            e.setReference(alert.reference());
            e.setType(alert.type().name());
            e.setSource(alert.source().name());
            e.setTitle(alert.title());
            e.setProviderId(alert.providerId());
            e.setPaymentReference(alert.paymentReference());
            e.setThreshold(alert.threshold());
            e.setFingerprint(alert.fingerprint());
            e.setDetectedAt(alert.detectedAt());
            e.setCreatedAt(now);
        }
        e.setSeverity(alert.severity().name());
        e.setStatus(alert.status().name());
        e.setDescription(alert.description());
        e.setIncidentId(alert.incidentId());
        e.setMetricValue(alert.metricValue());
        e.setLastSeenAt(alert.lastSeenAt());
        e.setAcknowledgedAt(alert.acknowledgedAt());
        e.setAcknowledgedBy(alert.acknowledgedBy());
        e.setResolvedAt(alert.resolvedAt());
        e.setResolvedBy(alert.resolvedBy());
        e.setResolutionNote(alert.resolutionNote());
        e.setUpdatedAt(now);
        return toDomain(jpa.save(e));
    }

    @Override
    public Optional<Alert> findByReference(long organizationId, String reference) {
        return jpa.findByOrganizationIdAndReference(organizationId, reference).map(AlertPersistenceAdapter::toDomain);
    }

    @Override
    public Optional<Alert> findUnresolvedByFingerprint(long organizationId, String fingerprint) {
        return jpa.findFirstByOrganizationIdAndFingerprintAndStatusNot(organizationId, fingerprint, AlertStatus.RESOLVED.name())
                .map(AlertPersistenceAdapter::toDomain);
    }

    @Override
    public PageResult<Alert> search(long organizationId, AlertStatus status, Severity severity, PageQuery page) {
        Page<AlertEntity> result = jpa.search(organizationId, status == null ? null : status.name(),
                severity == null ? null : severity.name(), PageRequest.of(page.page(), page.size()));
        return new PageResult<>(result.getContent().stream().map(AlertPersistenceAdapter::toDomain).toList(),
                page.page(), page.size(), result.getTotalElements());
    }

    @Override
    public List<Alert> findUnresolvedNotSeenSince(Instant before, int limit) {
        return jpa.findQuiet(before, PageRequest.of(0, limit)).stream().map(AlertPersistenceAdapter::toDomain).toList();
    }

    @Override
    public List<Alert> findAwaitingAcknowledgement(Severity minSeverity, int limit) {
        List<String> severities = java.util.Arrays.stream(Severity.values()).filter(s -> s.isAtLeast(minSeverity))
                .map(Severity::name).toList();
        return jpa.findAwaitingAcknowledgement(severities, PageRequest.of(0, limit)).stream()
                .map(AlertPersistenceAdapter::toDomain).toList();
    }

    @Override
    public Map<AlertStatus, Long> countByStatus(long organizationId) {
        Map<AlertStatus, Long> counts = new EnumMap<>(AlertStatus.class);
        jpa.countByStatus(organizationId).forEach(row -> counts.put(AlertStatus.valueOf((String) row[0]), (Long) row[1]));
        return counts;
    }

    @Override
    public Map<Severity, Long> countUnresolvedBySeverity(long organizationId) {
        Map<Severity, Long> counts = new EnumMap<>(Severity.class);
        for (Severity s : Severity.values()) {
            counts.put(s, 0L);
        }
        jpa.countUnresolvedBySeverity(organizationId).forEach(row -> counts.put(Severity.valueOf((String) row[0]), (Long) row[1]));
        return counts;
    }

    @Override
    public long countResolvedSince(long organizationId, Instant since) {
        return jpa.countByOrganizationIdAndStatusAndResolvedAtAfter(organizationId, AlertStatus.RESOLVED.name(), since);
    }

    private static Alert toDomain(AlertEntity e) {
        return new Alert(e.getId(), e.getOrganizationId(), e.getReference(), AlertType.valueOf(e.getType()),
                Severity.valueOf(e.getSeverity()), AlertStatus.valueOf(e.getStatus()), AlertSource.valueOf(e.getSource()),
                e.getTitle(), e.getDescription(), e.getProviderId(), e.getPaymentReference(), e.getIncidentId(),
                e.getMetricValue(), e.getThreshold(), e.getFingerprint(), e.getDetectedAt(), e.getAcknowledgedAt(),
                e.getAcknowledgedBy(), e.getResolvedAt(), e.getResolvedBy(), e.getResolutionNote(), e.getLastSeenAt());
    }
}
