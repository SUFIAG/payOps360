package com.payOps.payops360.analytics.application.port.input;

import com.payOps.payops360.analytics.domain.model.AnalyticsReport;

import java.time.Instant;

/**
 * Input Port: Generate Analytics Report Use Case
 */
public interface GenerateAnalyticsReportUseCase {
    AnalyticsReport generatePaymentTrends(AnalyticsQuery query);
    AnalyticsReport generateProviderPerformance(AnalyticsQuery query);

    record AnalyticsQuery(
            Instant startTime,
            Instant endTime,
            String dimension,  // HOURLY, DAILY, WEEKLY
            String providerId
    ) {}
}

