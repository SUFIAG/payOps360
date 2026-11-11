package com.payops.intelligence.application.port.input;

import com.payops.intelligence.domain.forecast.VolumeForecaster;
import com.payops.intelligence.domain.routing.RoutingAdvisor;
import com.payops.shared.domain.Severity;

import java.time.Instant;
import java.util.List;

/**
 * Continuously computed operational intelligence for the command-center dashboard.
 */
public interface InsightsUseCase {

    OperationalInsights insights(long organizationId);

    record OperationalInsights(int healthScore, String healthStatus, String headline, List<Anomaly> anomalies,
                               List<Recommendation> recommendations, List<VolumeForecaster.Point> forecast,
                               List<RoutingAdvisor.Ranking> routing, String engine, Instant generatedAt) {
    }

    record Anomaly(String type, String providerCode, String providerName, String metric, double observed,
                   double baseline, double deviationPercent, Severity severity, String explanation) {
    }

    record Recommendation(int priority, String category, String title, String detail, String action) {
    }
}
