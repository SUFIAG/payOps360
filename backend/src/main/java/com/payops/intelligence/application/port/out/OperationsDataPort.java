package com.payops.intelligence.application.port.out;

import com.payops.alert.application.port.in.AlertQueryUseCase;
import com.payops.analytics.domain.AnalyticsModels;
import com.payops.incident.application.port.in.IncidentQueryUseCase;
import com.payops.incident.application.port.in.IncidentView;
import com.payops.payment.application.port.in.PaymentQueryUseCase;
import com.payops.payment.application.port.in.PaymentView;
import com.payops.payment.domain.PaymentSearchCriteria;
import com.payops.provider.application.port.in.ProviderView;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Read-only operational data the AI features reason over. Every call is
 * scoped to a single tenant.
 */
public interface OperationsDataPort {

    AnalyticsModels.Overview overview(long organizationId, Duration window);

    List<AnalyticsModels.TimeBucket> hourlySeries(long organizationId, Duration window);

    List<AnalyticsModels.ProviderPerformance> providerPerformance(long organizationId, Duration window);

    List<AnalyticsModels.Breakdown> failureReasons(long organizationId, Duration window);

    List<AnalyticsModels.Corridor> corridors(long organizationId, Duration window);

    List<ProviderView> providers(long organizationId);

    List<IncidentView> activeIncidents(long organizationId);

    Optional<IncidentQueryUseCase.IncidentDetails> incident(long organizationId, String reference);

    AlertQueryUseCase.Summary alertSummary(long organizationId);

    Optional<PaymentQueryUseCase.PaymentDetails> payment(long organizationId, String reference);

    List<PaymentView> searchPayments(long organizationId, PaymentSearchCriteria criteria, int limit);

    Optional<Long> providerId(long organizationId, String code);
}
