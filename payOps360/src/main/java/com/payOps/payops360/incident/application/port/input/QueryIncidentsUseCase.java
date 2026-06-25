package com.payOps360.incident.application.port.input;

import com.payOps.payops360.common.dto.PagedResponse;
import com.payOps.payops360.incident.domain.model.Incident;
import com.payOps.payops360.incident.domain.model.IncidentStatus;

import java.util.UUID;

/**
 * Input Port: Query Incidents Use Case
 */
public interface QueryIncidentsUseCase {
    Incident getById(UUID id);
    PagedResponse<Incident> findAll(IncidentQueryParams params);

    record IncidentQueryParams(
            IncidentStatus status,
            String providerId,
            String severity,
            int page,
            int size
    ) {}
}

