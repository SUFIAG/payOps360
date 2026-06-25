package com.payOps.payops360.incident.application.port.output;

import com.payOps.payops360.common.dto.PagedResponse;
import com.payOps.payops360.incident.application.port.input.QueryIncidentsUseCase;
import com.payOps.payops360.incident.domain.model.Incident;

import java.util.Optional;
import java.util.UUID;

/**
 * Output Port: Incident Repository
 */
public interface IncidentRepository {
    Incident save(Incident incident);
    Optional<Incident> findById(UUID id);
    PagedResponse<Incident> findAll(QueryIncidentsUseCase.IncidentQueryParams params);
}

