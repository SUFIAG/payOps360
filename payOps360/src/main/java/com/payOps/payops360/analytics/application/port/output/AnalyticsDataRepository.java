package com.payOps.payops360.analytics.application.port.output;

import com.payOps.payops360.analytics.domain.model.MetricDataPoint;

import java.time.Instant;
import java.util.List;

/**
 * Output Port: Analytics Data Repository
 */
public interface AnalyticsDataRepository {
    List<MetricDataPoint> getPaymentMetrics(Instant startTime, Instant endTime, String dimension);
    List<MetricDataPoint> getProviderMetrics(String providerId, Instant startTime, Instant endTime);
    void saveMetricDataPoint(MetricDataPoint dataPoint);
}

