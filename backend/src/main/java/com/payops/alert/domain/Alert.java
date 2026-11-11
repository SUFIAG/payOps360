package com.payops.alert.domain;

import com.payops.shared.domain.DomainException;
import com.payops.shared.domain.References;
import com.payops.shared.domain.Severity;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

/**
 * Operational alert. Alerts sharing a fingerprint are de-duplicated while open.
 */
public class Alert {

    private final Long id;
    private final long organizationId;
    private final String reference;
    private final AlertType type;
    private Severity severity;
    private AlertStatus status;
    private final AlertSource source;
    private final String title;
    private String description;
    private final Long providerId;
    private final String paymentReference;
    private Long incidentId;
    private BigDecimal metricValue;
    private final BigDecimal threshold;
    private final String fingerprint;
    private final Instant detectedAt;
    private Instant acknowledgedAt;
    private String acknowledgedBy;
    private Instant resolvedAt;
    private String resolvedBy;
    private String resolutionNote;
    private Instant lastSeenAt;

    @SuppressWarnings("java:S107")
    public Alert(Long id, long organizationId, String reference, AlertType type, Severity severity, AlertStatus status,
                 AlertSource source, String title, String description, Long providerId, String paymentReference,
                 Long incidentId, BigDecimal metricValue, BigDecimal threshold, String fingerprint, Instant detectedAt,
                 Instant acknowledgedAt, String acknowledgedBy, Instant resolvedAt, String resolvedBy,
                 String resolutionNote, Instant lastSeenAt) {
        this.id = id;
        this.organizationId = organizationId;
        this.reference = reference;
        this.type = type;
        this.severity = severity;
        this.status = status;
        this.source = source;
        this.title = title;
        this.description = description;
        this.providerId = providerId;
        this.paymentReference = paymentReference;
        this.incidentId = incidentId;
        this.metricValue = metricValue;
        this.threshold = threshold;
        this.fingerprint = fingerprint;
        this.detectedAt = detectedAt;
        this.acknowledgedAt = acknowledgedAt;
        this.acknowledgedBy = acknowledgedBy;
        this.resolvedAt = resolvedAt;
        this.resolvedBy = resolvedBy;
        this.resolutionNote = resolutionNote;
        this.lastSeenAt = lastSeenAt;
    }

    @SuppressWarnings("java:S107")
    public static Alert raise(long organizationId, AlertType type, Severity severity, AlertSource source, String title,
                              String description, Long providerId, String paymentReference, BigDecimal metricValue,
                              BigDecimal threshold, String fingerprint, Instant now) {
        return new Alert(null, organizationId, References.next("ALR"), type, severity, AlertStatus.OPEN, source, title,
                description, providerId, paymentReference, null, metricValue, threshold, fingerprint, now, null, null,
                null, null, null, now);
    }

    /** The same condition fired again while the alert is open: refresh it, escalating severity if needed. */
    public void recur(Severity observedSeverity, BigDecimal observedValue, String latestDescription, Instant now) {
        if (observedSeverity.isAtLeast(severity)) {
            severity = observedSeverity;
        }
        metricValue = observedValue;
        description = latestDescription;
        lastSeenAt = now;
    }

    public void acknowledge(String actor, Instant now) {
        if (status != AlertStatus.OPEN) {
            throw new DomainException("ALERT_NOT_OPEN", "Only open alerts can be acknowledged");
        }
        status = AlertStatus.ACKNOWLEDGED;
        acknowledgedAt = now;
        acknowledgedBy = actor;
    }

    public void resolve(String actor, String note, Instant now) {
        if (status == AlertStatus.RESOLVED) {
            throw new DomainException("ALERT_RESOLVED", "Alert is already resolved");
        }
        status = AlertStatus.RESOLVED;
        resolvedAt = now;
        resolvedBy = actor;
        resolutionNote = note;
    }

    /** Auto-resolve once the condition has not recurred for the quiet period. */
    public boolean autoResolveIfQuiet(Duration quietPeriod, Instant now) {
        if (status != AlertStatus.RESOLVED && lastSeenAt.plus(quietPeriod).isBefore(now)) {
            resolve("system", "Auto-resolved: condition cleared for " + quietPeriod.toMinutes() + " minutes", now);
            return true;
        }
        return false;
    }

    public void linkIncident(long incidentId) {
        this.incidentId = incidentId;
    }

    public Long id() { return id; }
    public long organizationId() { return organizationId; }
    public String reference() { return reference; }
    public AlertType type() { return type; }
    public Severity severity() { return severity; }
    public AlertStatus status() { return status; }
    public AlertSource source() { return source; }
    public String title() { return title; }
    public String description() { return description; }
    public Long providerId() { return providerId; }
    public String paymentReference() { return paymentReference; }
    public Long incidentId() { return incidentId; }
    public BigDecimal metricValue() { return metricValue; }
    public BigDecimal threshold() { return threshold; }
    public String fingerprint() { return fingerprint; }
    public Instant detectedAt() { return detectedAt; }
    public Instant acknowledgedAt() { return acknowledgedAt; }
    public String acknowledgedBy() { return acknowledgedBy; }
    public Instant resolvedAt() { return resolvedAt; }
    public String resolvedBy() { return resolvedBy; }
    public String resolutionNote() { return resolutionNote; }
    public Instant lastSeenAt() { return lastSeenAt; }
}
