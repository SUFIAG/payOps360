package com.payops.incident.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * AI-generated incident analysis. Always advisory: a human owns the decision.
 */
public record AiAnalysis(String summary, String rootCause, List<String> recommendations, BigDecimal confidence,
                         String engine, Instant generatedAt) {

    public AiAnalysis {
        recommendations = recommendations == null ? List.of() : List.copyOf(recommendations);
    }
}
