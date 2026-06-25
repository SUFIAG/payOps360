package com.payOps.payops360.ai.application.port.output;

import com.payOps.payops360.ai.domain.model.AIInvestigation;

import java.util.Optional;
import java.util.UUID;

/**
 * Output Port: AI Investigation Repository
 */
public interface AIInvestigationRepository {
    AIInvestigation save(AIInvestigation investigation);
    Optional<AIInvestigation> findById(UUID id);
    Optional<AIInvestigation> findByIncidentId(UUID incidentId);
}

