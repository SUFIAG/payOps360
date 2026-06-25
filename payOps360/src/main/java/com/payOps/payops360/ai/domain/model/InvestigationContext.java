package com.payOps.payops360.ai.domain.model;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * Context Data for AI Investigation
 * Aggregates all relevant information for intelligent analysis
 */
@Getter
@Builder
public class InvestigationContext {
    private final String incidentId;
    private final String incidentTitle;
    private final String incidentCategory;
    private final String severity;
    private final int impactedPaymentCount;
    private final List<String> relatedAlertTypes;
    private final List<String> failureCategories;
    private final Map<String, Object> providerHealth;
    private final List<String> recentErrors;
    private final Map<String, Double> systemMetrics;
    private final String timeWindow;

    public String toContextString() {
        StringBuilder context = new StringBuilder();
        context.append("Incident: ").append(incidentTitle).append("\n");
        context.append("Category: ").append(incidentCategory).append("\n");
        context.append("Severity: ").append(severity).append("\n");
        context.append("Impact: ").append(impactedPaymentCount).append(" payments affected\n");

        if (relatedAlertTypes != null && !relatedAlertTypes.isEmpty()) {
            context.append("Alert Types: ").append(String.join(", ", relatedAlertTypes)).append("\n");
        }

        if (failureCategories != null && !failureCategories.isEmpty()) {
            context.append("Failure Categories: ").append(String.join(", ", failureCategories)).append("\n");
        }

        if (recentErrors != null && !recentErrors.isEmpty()) {
            context.append("Recent Errors:\n");
            recentErrors.stream().limit(5).forEach(error -> context.append("- ").append(error).append("\n"));
        }

        return context.toString();
    }
}

