package com.payOps.payops360.ai.domain.model;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * AI Investigation Domain Model (Pure Domain - No Framework Dependencies)
 * Represents an AI-powered root cause analysis investigation
 */
@Getter
@Builder
public class AIInvestigation {
    private final UUID id;
    private final UUID incidentId;
    private final String investigationType;  // ROOT_CAUSE_ANALYSIS, PATTERN_ANALYSIS, PREDICTION
    private final String context;  // Aggregated context for AI
    private final String prompt;  // Generated prompt for LLM
    private final String aiResponse;  // Raw AI response
    private final String rootCause;  // Extracted root cause
    private final List<String> recommendations;  // Actionable recommendations
    private final Double confidenceScore;  // AI confidence (0.0 to 1.0)
    private final String model;  // AI model used (gpt-4, claude-3, etc.)
    private final Instant investigatedAt;
    private final Long processingTimeMs;

    public boolean isHighConfidence() {
        return confidenceScore != null && confidenceScore >= 0.8;
    }

    public boolean requiresHumanReview() {
        return confidenceScore == null || confidenceScore < 0.6;
    }
}

