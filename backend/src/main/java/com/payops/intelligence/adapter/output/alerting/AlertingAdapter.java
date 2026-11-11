package com.payops.intelligence.adapter.output.alerting;

import com.payops.alert.application.port.input.RaiseAlertUseCase;
import com.payops.alert.domain.AlertSource;
import com.payops.alert.domain.AlertType;
import com.payops.intelligence.application.port.input.InsightsUseCase.Anomaly;
import com.payops.intelligence.application.port.output.AlertingPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
class AlertingAdapter implements AlertingPort {

    private final RaiseAlertUseCase alerts;

    @Override
    public void raiseAnomaly(long organizationId, long providerId, Anomaly anomaly) {
        AlertType type = switch (anomaly.type()) {
            case "PROVIDER_DOWN" -> AlertType.PROVIDER_DOWN;
            case "LATENCY_SPIKE" -> AlertType.LATENCY_SPIKE;
            case "VOLUME_DROP" -> AlertType.VOLUME_DROP;
            case "VOLUME_SURGE" -> AlertType.VOLUME_SURGE;
            default -> AlertType.FAILURE_RATE_SPIKE;
        };
        String title = switch (type) {
            case PROVIDER_DOWN -> anomaly.providerName() + " appears to be down";
            case LATENCY_SPIKE -> "Latency spike on " + anomaly.providerName();
            case VOLUME_DROP -> "Traffic drop on " + anomaly.providerName();
            case VOLUME_SURGE -> "Traffic surge on " + anomaly.providerName();
            default -> "Failure-rate spike on " + anomaly.providerName();
        };
        alerts.raise(new RaiseAlertUseCase.RaiseAlertCommand(organizationId, type, anomaly.severity(), AlertSource.AI,
                title, anomaly.explanation(), providerId, null, BigDecimal.valueOf(anomaly.observed()),
                BigDecimal.valueOf(anomaly.baseline()), "ai:" + type.name() + ":" + providerId));
    }
}
