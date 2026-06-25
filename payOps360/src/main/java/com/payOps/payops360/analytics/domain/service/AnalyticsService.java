package com.payOps/payops360.analytics.domain.service;

import com.payOps/payops360.analytics.domain.model.AnalyticsReport;
import com.payOps/payops360.analytics.domain.model.MetricDataPoint;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Domain Service for Analytics and Trend Analysis (Pure Domain Logic)
 */
public class AnalyticsService {

    /**
     * Generate payment trends report
     */
    public AnalyticsReport generatePaymentTrendsReport(
            List<MetricDataPoint> dataPoints,
            Instant startTime,
            Instant endTime
    ) {
        List<AnalyticsReport.Insight> insights = analyzePaymentTrends(dataPoints);

        return AnalyticsReport.builder()
                .reportId(UUID.randomUUID().toString())
                .reportType("PAYMENT_TRENDS")
                .startTime(startTime)
                .endTime(endTime)
                .dataPoints(dataPoints)
                .insights(insights)
                .generatedAt(Instant.now())
                .build();
    }

    /**
     * Generate provider performance report
     */
    public AnalyticsReport generateProviderPerformanceReport(
            List<MetricDataPoint> dataPoints,
            Instant startTime,
            Instant endTime
    ) {
        List<AnalyticsReport.Insight> insights = analyzeProviderPerformance(dataPoints);

        return AnalyticsReport.builder()
                .reportId(UUID.randomUUID().toString())
                .reportType("PROVIDER_PERFORMANCE")
                .startTime(startTime)
                .endTime(endTime)
                .dataPoints(dataPoints)
                .insights(insights)
                .generatedAt(Instant.now())
                .build();
    }

    /**
     * Detect anomalies in metrics
     */
    public List<MetricDataPoint> detectAnomalies(List<MetricDataPoint> dataPoints) {
        if (dataPoints == null || dataPoints.isEmpty()) {
            return List.of();
        }

        // Calculate mean and standard deviation
        double mean = dataPoints.stream()
                .mapToDouble(MetricDataPoint::getValue)
                .average()
                .orElse(0.0);

        double variance = dataPoints.stream()
                .mapToDouble(dp -> Math.pow(dp.getValue() - mean, 2))
                .average()
                .orElse(0.0);

        double stdDev = Math.sqrt(variance);
        double threshold = mean + (2 * stdDev);  // 2 standard deviations

        // Filter anomalies
        return dataPoints.stream()
                .filter(dp -> dp.isAnomaly(threshold))
                .toList();
    }

    private List<AnalyticsReport.Insight> analyzePaymentTrends(List<MetricDataPoint> dataPoints) {
        List<AnalyticsReport.Insight> insights = new ArrayList<>();

        if (dataPoints.isEmpty()) {
            return insights;
        }

        // Calculate average payment volume
        double avgVolume = dataPoints.stream()
                .mapToDouble(MetricDataPoint::getValue)
                .average()
                .orElse(0.0);

        // Check for declining trend
        if (dataPoints.size() >= 3) {
            double first = dataPoints.get(0).getValue();
            double last = dataPoints.get(dataPoints.size() - 1).getValue();

            if (last < first * 0.9) {  // 10% decline
                insights.add(AnalyticsReport.Insight.builder()
                        .type("DECLINING_TREND")
                        .description("Payment volume has declined by more than 10%")
                        .severity("HIGH")
                        .recommendation("Investigate potential issues affecting payment processing")
                        .build());
            }
        }

        // Check for spikes
        List<MetricDataPoint> anomalies = detectAnomalies(dataPoints);
        if (!anomalies.isEmpty()) {
            insights.add(AnalyticsReport.Insight.builder()
                    .type("VOLUME_SPIKE")
                    .description(String.format("Detected %d anomalous data points", anomalies.size()))
                    .severity("MEDIUM")
                    .recommendation("Review system capacity and scaling policies")
                    .build());
        }

        return insights;
    }

    private List<AnalyticsReport.Insight> analyzeProviderPerformance(List<MetricDataPoint> dataPoints) {
        List<AnalyticsReport.Insight> insights = new ArrayList<>();

        if (dataPoints.isEmpty()) {
            return insights;
        }

        double avgPerformance = dataPoints.stream()
                .mapToDouble(MetricDataPoint::getValue)
                .average()
                .orElse(0.0);

        // Check for poor performance
        if (avgPerformance < 0.95) {  // Below 95% success rate
            insights.add(AnalyticsReport.Insight.builder()
                    .type("LOW_SUCCESS_RATE")
                    .description(String.format("Average success rate is %.2f%%, below threshold", avgPerformance * 100))
                    .severity("CRITICAL")
                    .recommendation("Consider switching to fallback provider or investigating provider issues")
                    .build());
        }

        // Check for degradation over time
        if (dataPoints.size() >= 3) {
            double first = dataPoints.get(0).getValue();
            double last = dataPoints.get(dataPoints.size() - 1).getValue();

            if (last < first * 0.95) {  // 5% degradation
                insights.add(AnalyticsReport.Insight.builder()
                        .type("PERFORMANCE_DEGRADATION")
                        .description("Provider performance has degraded over time")
                        .severity("HIGH")
                        .recommendation("Monitor provider closely and prepare fallback options")
                        .build());
            }
        }

        return insights;
    }
}

