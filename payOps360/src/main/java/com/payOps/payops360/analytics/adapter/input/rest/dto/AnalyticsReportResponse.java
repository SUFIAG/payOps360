package com.payOps.payops360.analytics.adapter.input.rest.dto;

import com.payOps.payops360.analytics.domain.model.MetricDataPoint;

import java.time.Instant;
import java.util.List;

/**
 * Response DTO for Analytics Report
 */
public record AnalyticsReportResponse(
        String reportId,
        String reportType,
        Instant startTime,
        Instant endTime,
        List<MetricDataPoint> dataPoints,
        List<InsightResponse> insights,
        Instant generatedAt
) {
    public record InsightResponse(
            String type,
            String description,
            String severity,
            String recommendation
    ) {}
}

