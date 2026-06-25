package com.payOps/payops360.ai.adapter.input.rest.mapper;

import com.payOps/payops360.ai.adapter.input.rest.dto.AIInvestigationResponse;
import com.payOps/payops360.ai.domain.model.AIInvestigation;
import org.springframework.stereotype.Component;

/**
 * Mapper for AI REST DTOs
 */
@Component
public class AIRestMapper {

    public AIInvestigationResponse toResponse(AIInvestigation investigation) {
        return new AIInvestigationResponse(
                investigation.getId(),
                investigation.getIncidentId(),
                investigation.getInvestigationType(),
                investigation.getRootCause(),
                investigation.getRecommendations(),
                investigation.getConfidenceScore(),
                investigation.getModel(),
                investigation.getInvestigatedAt(),
                investigation.getProcessingTimeMs(),
                investigation.isHighConfidence(),
                investigation.requiresHumanReview()
        );
    }
}

