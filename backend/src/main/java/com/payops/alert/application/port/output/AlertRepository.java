package com.payops.alert.application.port.output;

import com.payops.alert.domain.Alert;
import com.payops.alert.domain.AlertStatus;
import com.payops.shared.domain.PageQuery;
import com.payops.shared.domain.PageResult;
import com.payops.shared.domain.Severity;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface AlertRepository {

    Alert save(Alert alert);

    Optional<Alert> findByReference(long organizationId, String reference);

    Optional<Alert> findUnresolvedByFingerprint(long organizationId, String fingerprint);

    PageResult<Alert> search(long organizationId, AlertStatus status, Severity severity, PageQuery page);

    List<Alert> findUnresolvedNotSeenSince(Instant before, int limit);

    /** Across tenants: OPEN (unacknowledged) alerts of at least the given severity, oldest first. */
    List<Alert> findAwaitingAcknowledgement(Severity minSeverity, int limit);

    Map<AlertStatus, Long> countByStatus(long organizationId);

    Map<Severity, Long> countUnresolvedBySeverity(long organizationId);

    long countResolvedSince(long organizationId, Instant since);
}
