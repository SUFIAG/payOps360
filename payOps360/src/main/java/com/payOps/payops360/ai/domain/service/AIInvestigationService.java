package com.payOps.payops360.ai.domain.service;

import com.payOps.payops360.ai.domain.model.AIInvestigation;
import com.payOps.payops360.ai.domain.model.InvestigationContext;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Domain Service for AI Investigation Logic (Pure Domain Logic)
 * Builds context and generates prompts for AI analysis
 */
public class AIInvestigationService {

    /**
     * Build investigation from context and AI response
     */
    public AIInvestigation buildInvestigation(
            UUID incidentId,
            InvestigationContext context,
            String aiResponse,
            String model,
            long processingTimeMs
    ) {
        String rootCause = extractRootCause(aiResponse);
        List<String> recommendations = extractRecommendations(aiResponse);
        Double confidence = calculateConfidence(aiResponse, context);

        return AIInvestigation.builder()
                .id(UUID.randomUUID())
                .incidentId(incidentId)
                .investigationType("ROOT_CAUSE_ANALYSIS")
                .context(context.toContextString())
                .prompt(generatePrompt(context))
                .aiResponse(aiResponse)
                .rootCause(rootCause)
                .recommendations(recommendations)
                .confidenceScore(confidence)
                .model(model)
                .investigatedAt(Instant.now())
                .processingTimeMs(processingTimeMs)
                .build();
    }

    /**
     * Generate AI prompt from context
     */
    public String generatePrompt(InvestigationContext context) {
        return String.format("""
                You are an expert payment operations analyst. Analyze the following incident and provide:
                1. Root cause analysis
                2. Step-by-step reasoning
                3. Actionable recommendations
                4. Prevention strategies
                
                Incident Details:
                %s
                
                Provide your analysis in a structured format:
                ROOT CAUSE: [Your analysis]
                REASONING: [Step-by-step explanation]
                RECOMMENDATIONS:
                1. [First recommendation]
                2. [Second recommendation]
                3. [Third recommendation]
                PREVENTION: [Long-term prevention strategies]
                """, context.toContextString());
    }

    /**
     * Extract root cause from AI response
     */
    private String extractRootCause(String aiResponse) {
        if (aiResponse == null || aiResponse.isEmpty()) {
            return "Unable to determine root cause";
        }

        // Simple extraction - look for ROOT CAUSE: section
        String[] lines = aiResponse.split("\n");
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].toUpperCase().contains("ROOT CAUSE:")) {
                StringBuilder rootCause = new StringBuilder();
                rootCause.append(lines[i].substring(lines[i].indexOf(":") + 1).trim());

                // Continue reading until next section
                for (int j = i + 1; j < lines.length; j++) {
                    if (lines[j].toUpperCase().contains("REASONING:") ||
                        lines[j].toUpperCase().contains("RECOMMENDATIONS:")) {
                        break;
                    }
                    if (!lines[j].trim().isEmpty()) {
                        rootCause.append(" ").append(lines[j].trim());
                    }
                }
                return rootCause.toString();
            }
        }

        // Fallback: return first substantial paragraph
        for (String line : lines) {
            if (line.trim().length() > 50) {
                return line.trim();
            }
        }

        return "Root cause analysis pending";
    }

    /**
     * Extract recommendations from AI response
     */
    private List<String> extractRecommendations(String aiResponse) {
        List<String> recommendations = new ArrayList<>();

        if (aiResponse == null || aiResponse.isEmpty()) {
            return recommendations;
        }

        String[] lines = aiResponse.split("\n");
        boolean inRecommendations = false;

        for (String line : lines) {
            if (line.toUpperCase().contains("RECOMMENDATIONS:")) {
                inRecommendations = true;continue;
            }

            if (inRecommendations) {
                // Stop at next section
                if (line.toUpperCase().contains("PREVENTION:") ||
                    line.toUpperCase().contains("CONCLUSION:")) {
                    break;
                }

                // Extract numbered recommendations
                String trimmed = line.trim();
                if (trimmed.matches("^\\d+\\..*") || trimmed.startsWith("-") || trimmed.startsWith("•")) {
                    String recommendation = trimmed.replaceFirst("^\\d+\\.\\s*", "")
                            .replaceFirst("^[-•]\\s*", "").trim();
                    if (!recommendation.isEmpty()) {
                        recommendations.add(recommendation);
                    }
                }
            }
        }

        // If no recommendations found, add a default one
        if (recommendations.isEmpty()) {
            recommendations.add("Monitor the situation and gather more data");
            recommendations.add("Review system logs for additional context");
            recommendations.add("Consider implementing additional alerts");
        }

        return recommendations;
    }

    /**
     * Calculate confidence score based on response quality and context
     */
    private Double calculateConfidence(String aiResponse, InvestigationContext context) {
        if (aiResponse == null || aiResponse.isEmpty()) {
            return 0.0;
        }

        double confidence = 0.5;  // Base confidence

        // Increase confidence if response is detailed
        if (aiResponse.length() > 200) {
            confidence += 0.1;
        }

        // Increase confidence if structured sections are present
        if (aiResponse.toUpperCase().contains("ROOT CAUSE:")) {
            confidence += 0.1;
        }
        if (aiResponse.toUpperCase().contains("RECOMMENDATIONS:")) {
            confidence += 0.1;
        }
        if (aiResponse.toUpperCase().contains("REASONING:")) {
            confidence += 0.1;
        }

        // Increase confidence if context has sufficient data
        if (context.getImpactedPaymentCount() > 10) {
            confidence += 0.05;
        }
        if (context.getRelatedAlertTypes() != null && context.getRelatedAlertTypes().size() > 2) {
            confidence += 0.05;
        }

        return Math.min(confidence, 1.0);  // Cap at 1.0
    }
}

