package com.payops.alert.adapter.out.context;

import com.payops.alert.application.port.out.AlertContextPort;
import com.payops.incident.application.port.in.IncidentCorrelationUseCase;
import com.payops.provider.application.port.in.ProviderCatalogUseCase;
import com.payops.provider.application.port.in.ProviderView;
import com.payops.shared.domain.Severity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
class AlertContextAdapter implements AlertContextPort {

    private final ProviderCatalogUseCase providers;
    private final IncidentCorrelationUseCase incidents;

    @Override
    public Optional<String> providerCode(long organizationId, long providerId) {
        return providers.findById(organizationId, providerId).map(ProviderView::code);
    }

    @Override
    public Optional<String> incidentReference(long organizationId, long incidentId) {
        return Optional.ofNullable(incidents.referenceOf(organizationId, incidentId));
    }

    @Override
    public Optional<Long> correlateIncident(long organizationId, Long providerId, Severity severity,
                                            String alertReference, String title, String description) {
        return Optional.of(incidents.correlate(organizationId, providerId, severity, alertReference, title, description));
    }
}
