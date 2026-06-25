package com.payOps.payops360.incident.application.port.input;

import com.payOps.payops360.incident.domain.model.Incident;
import com.payOps.payops360.incident.domain.service.IncidentCorrelationService;

import java.util.List;

/**
 * Input Port: Create Incident Use Case
 */
public interface CreateIncidentUseCase {
    Incident create(CreateIncidentCommand command);

    record CreateIncidentCommand(
            List<IncidentCorrelationService.AlertData> alerts,
            String providerId,
            String region
    ) {}
}

