package com.payops.intelligence.adapter.output.operations;

import com.payops.alert.application.port.input.AlertQueryUseCase;
import com.payops.analytics.application.port.input.AnalyticsUseCase;
import com.payops.analytics.domain.AnalyticsModels;
import com.payops.incident.application.port.input.IncidentQueryUseCase;
import com.payops.incident.application.port.input.IncidentView;
import com.payops.intelligence.application.port.output.OperationsDataPort;
import com.payops.payment.application.port.input.PaymentQueryUseCase;
import com.payops.payment.application.port.input.PaymentView;
import com.payops.payment.domain.PaymentSearchCriteria;
import com.payops.provider.application.port.input.ProviderCatalogUseCase;
import com.payops.provider.application.port.input.ProviderView;
import com.payops.shared.domain.NotFoundException;
import com.payops.shared.domain.PageQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Gives the intelligence module read access to other modules strictly through
 * their input ports.
 */
@Component
@RequiredArgsConstructor
class OperationsDataAdapter implements OperationsDataPort {

    private final AnalyticsUseCase analytics;
    private final ProviderCatalogUseCase providers;
    private final IncidentQueryUseCase incidents;
    private final AlertQueryUseCase alerts;
    private final PaymentQueryUseCase payments;

    @Override
    public AnalyticsModels.Overview overview(long organizationId, Duration window) {
        return analytics.overview(organizationId, window);
    }

    @Override
    public List<AnalyticsModels.TimeBucket> hourlySeries(long organizationId, Duration window) {
        return analytics.timeseries(organizationId, window, Duration.ofHours(1));
    }

    @Override
    public List<AnalyticsModels.ProviderPerformance> providerPerformance(long organizationId, Duration window) {
        return analytics.providerPerformance(organizationId, window);
    }

    @Override
    public List<AnalyticsModels.Breakdown> failureReasons(long organizationId, Duration window) {
        return analytics.failureReasons(organizationId, window);
    }

    @Override
    public List<AnalyticsModels.Corridor> corridors(long organizationId, Duration window) {
        return analytics.corridors(organizationId, window);
    }

    @Override
    public List<ProviderView> providers(long organizationId) {
        return providers.list(organizationId);
    }

    @Override
    public List<IncidentView> activeIncidents(long organizationId) {
        return incidents.active(organizationId);
    }

    @Override
    public Optional<IncidentQueryUseCase.IncidentDetails> incident(long organizationId, String reference) {
        try {
            return Optional.of(incidents.get(organizationId, reference));
        } catch (NotFoundException e) {
            return Optional.empty();
        }
    }

    @Override
    public AlertQueryUseCase.Summary alertSummary(long organizationId) {
        return alerts.summary(organizationId);
    }

    @Override
    public Optional<PaymentQueryUseCase.PaymentDetails> payment(long organizationId, String reference) {
        try {
            return Optional.of(payments.get(organizationId, reference));
        } catch (NotFoundException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<PaymentView> searchPayments(long organizationId, PaymentSearchCriteria criteria, int limit) {
        return payments.search(organizationId, criteria, PageQuery.of(0, limit)).items();
    }

    @Override
    public Optional<Long> providerId(long organizationId, String code) {
        return payments.providerIdFor(organizationId, code);
    }
}
