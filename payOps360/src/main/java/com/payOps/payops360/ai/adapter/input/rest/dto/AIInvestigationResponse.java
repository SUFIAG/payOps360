package com.payOps/payops360.ai.adapter.input.rest.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for AI Investigation
 */
public record AIInvestigationResponse(
        UUID id,
        UUID incidentId,
        String investigationType,
        String rootCause,
        List<String> recommendations,
        Double confidenceScore,
        String model,
        Instant investigatedAt,
        Long processingTimeMs,
        boolean highConfidence,
        boolean requiresHumanReview
) {}

