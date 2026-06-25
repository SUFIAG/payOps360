package com.payOps.payops360.ai.application.port.input;

import com.payOps.payops360.ai.domain.model.AIInvestigation;

import java.util.UUID;

/**
 * Input Port: Query AI Investigations Use Case
 */
public interface QueryAIInvestigationsUseCase {
    AIInvestigation getById(UUID id);
    AIInvestigation getByIncidentId(UUID incidentId);
}

