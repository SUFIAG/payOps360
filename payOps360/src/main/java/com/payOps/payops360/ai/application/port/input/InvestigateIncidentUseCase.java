package com.payOps.payops360.ai.application.port.input;

import com.payOps.payops360.ai.domain.model.AIInvestigation;

import java.util.UUID;

/**
 * Input Port: Investigate Incident Use Case
 */
public interface InvestigateIncidentUseCase {
    AIInvestigation investigate(InvestigateCommand command);

    record InvestigateCommand(
            UUID incidentId,
            String model  // "gpt-4", "claude-3", "mock" (for testing)
    ) {}
}

