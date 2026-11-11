package com.payops.incident.application.port.output;

import com.payops.incident.domain.Incident;
import com.payops.incident.domain.IncidentStatus;
import com.payops.incident.domain.TimelineEntry;
import com.payops.shared.domain.PageQuery;
import com.payops.shared.domain.PageResult;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface IncidentRepository {

    Incident save(Incident incident);

    Optional<Incident> findByReference(long organizationId, String reference);

    Optional<Incident> findById(long organizationId, long id);

    Optional<Incident> findActiveForProvider(long organizationId, Long providerId, Instant openedAfter);

    PageResult<Incident> search(long organizationId, IncidentStatus status, PageQuery page);

    List<Incident> findActive(long organizationId);

    List<TimelineEntry> timeline(long incidentId);

    Map<IncidentStatus, Long> countByStatus(long organizationId);

    List<Incident> findResolvedSince(long organizationId, Instant since);

    /** Across tenants: incidents with an acknowledge or resolve deadline passed and not yet flagged. */
    List<Incident> findSlaDue(Instant now, int limit);

    /** Across tenants: OPEN (unacknowledged) incidents, oldest first. */
    List<Incident> findAwaitingAcknowledgement(int limit);
}
