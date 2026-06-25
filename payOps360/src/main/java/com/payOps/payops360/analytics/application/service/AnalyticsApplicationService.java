package com.payOps.payops360.analytics.application.service;

import com.payOps.payops360.analytics.application.port.input.*;
import com.payOps.payops360.analytics.application.port.output.AnalyticsDataRepository;
import com.payOps.payops360.analytics.domain.model.AnalyticsReport;
import com.payOps.payops360.analytics.domain.model.MetricDataPoint;
import com.payOps.payops360.analytics.domain.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Application Service implementing Analytics Use Cases
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AnalyticsApplicationService implements
        GenerateAnalyticsReportUseCase,
        DetectAnomaliesUseCase {

    private final AnalyticsDataRepository analyticsDataRepository;
    private final AnalyticsService analyticsService;

    @Override
    public AnalyticsReport generatePaymentTrends(AnalyticsQuery query) {
        log.info("Generating payment trends report from {} to {}", query.startTime(), query.endTime());

        List<MetricDataPoint> dataPoints = analyticsDataRepository.getPaymentMetrics(
                query.startTime(),
                query.endTime(),
                query.dimension()
        );

        AnalyticsReport report = analyticsService.generatePaymentTrendsReport(
                dataPoints,
                query.startTime(),
                query.endTime()
        );

        log.info("Payment trends report generated with {} data points and {} insights",
                report.getDataPointCount(), report.getInsightCount());

        return report;
    }

    @Override
    public AnalyticsReport generateProviderPerformance(AnalyticsQuery query) {
        log.info("Generating provider performance report for provider: {}", query.providerId());

        List<MetricDataPoint> dataPoints = analyticsDataRepository.getProviderMetrics(
                query.providerId(),
                query.startTime(),
                query.endTime()
        );

        AnalyticsReport report = analyticsService.generateProviderPerformanceReport(
                dataPoints,
                query.startTime(),
                query.endTime()
        );

        log.info("Provider performance report generated with {} data points and {} insights",
                report.getDataPointCount(), report.getInsightCount());

        return report;
    }

    @Override
    public List<MetricDataPoint> detectAnomalies(AnomalyDetectionQuery query) {
        log.info("Detecting anomalies for metric: {}", query.metricName());

        List<MetricDataPoint> anomalies = analyticsService.detectAnomalies(query.dataPoints());

        log.info("Detected {} anomalies out of {} data points", anomalies.size(), query.dataPoints().size());

        return anomalies;
    }
}

