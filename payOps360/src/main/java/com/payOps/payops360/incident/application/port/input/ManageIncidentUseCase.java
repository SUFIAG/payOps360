package com.payOps.payops360.incident.application.port.input;

import com.payOps.payops360.incident.domain.model.Incident;

import java.util.UUID;

/**
 * Input Port: Manage Incident Use Cases
 */
public interface ManageIncidentUseCase {
    Incident acknowledge(UUID incidentId, String assignedTo);
    Incident resolve(UUID incidentId, String rootCause, String resolution);
    Incident escalate(UUID incidentId);
}

