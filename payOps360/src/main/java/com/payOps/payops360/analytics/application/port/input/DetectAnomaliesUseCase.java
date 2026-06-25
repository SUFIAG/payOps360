package com.payOps.payops360.analytics.application.port.input;

import com.payOps.payops360.analytics.domain.model.MetricDataPoint;

import java.util.List;

/**
 * Input Port: Detect Anomalies Use Case
 */
public interface DetectAnomaliesUseCase {
    List<MetricDataPoint> detectAnomalies(AnomalyDetectionQuery query);

    record AnomalyDetectionQuery(
            String metricName,
            List<MetricDataPoint> dataPoints
    ) {}
}

