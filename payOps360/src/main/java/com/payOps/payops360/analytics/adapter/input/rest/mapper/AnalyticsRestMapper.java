package com.payOps.payops360.analytics.adapter.input.rest.mapper;

import com.payOps.payops360.analytics.adapter.input.rest.dto.AnalyticsReportResponse;
import com.payOps.payops360.analytics.domain.model.AnalyticsReport;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

/**
 * Mapper for Analytics REST DTOs
 */
@Component
public class AnalyticsRestMapper {

    public AnalyticsReportResponse toResponse(AnalyticsReport report) {
        return new AnalyticsReportResponse(
                report.getReportId(),
                report.getReportType(),
                report.getStartTime(),
                report.getEndTime(),
                report.getData Points(),
                report.getInsights().stream()
                        .map(insight -> new AnalyticsReportResponse.InsightResponse(
                                insight.getType(),
                                insight.getDescription(),
                                insight.getSeverity(),
                                insight.getRecommendation()
                        ))
                        .collect(Collectors.toList()),
                report.getGeneratedAt()
        );
    }
}

