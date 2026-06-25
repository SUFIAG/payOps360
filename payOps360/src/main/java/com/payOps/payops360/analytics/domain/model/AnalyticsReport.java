package com.payOps/payops360.analytics.domain.model;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

/**
 * Analytics Report containing time-series data and insights
 */
@Getter
@Builder
public class AnalyticsReport {
    private final String reportId;
    private final String reportType;  // PAYMENT_TRENDS, PROVIDER_PERFORMANCE, FAILURE_ANALYSIS
    private final Instant startTime;
    private final Instant endTime;
    private final List<MetricDataPoint> dataPoints;
    private final List<Insight> insights;
    private final Instant generatedAt;

    @Getter
    @Builder
    public static class Insight {
        private final String type;
        private final String description;
        private final String severity;
        private final String recommendation;
    }

    public int getDataPointCount() {
        return dataPoints != null ? dataPoints.size() : 0;
    }

    public int getInsightCount() {
        return insights != null ? insights.size() : 0;
    }
}

