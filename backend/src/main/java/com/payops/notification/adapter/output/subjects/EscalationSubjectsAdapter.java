package com.payops.notification.adapter.output.subjects;

import com.payops.alert.application.port.input.AlertQueryUseCase;
import com.payops.incident.application.port.input.IncidentEscalationUseCase;
import com.payops.notification.application.port.output.EscalationSubjectsPort;
import com.payops.shared.domain.Severity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Unacknowledged incidents and critical alerts, read through the incident and
 * alert input ports.
 */
@Component
@RequiredArgsConstructor
class EscalationSubjectsAdapter implements EscalationSubjectsPort {

    private final IncidentEscalationUseCase incidents;
    private final AlertQueryUseCase alerts;

    @Override
    public List<Subject> awaitingAcknowledgement(int limit) {
        List<Subject> subjects = new ArrayList<>();
        incidents.awaitingAcknowledgement(limit).forEach(i -> subjects.add(new Subject(i.organizationId(), "INCIDENT",
                i.reference(), i.severity(), i.title(), i.providerCode(), i.openedAt())));
        alerts.awaitingAcknowledgement(Severity.CRITICAL, limit).forEach(a -> subjects.add(new Subject(a.organizationId(),
                "ALERT", a.reference(), a.severity(), a.title(), a.providerCode(), a.detectedAt())));
        return subjects;
    }

    @Override
    public void recordIncidentEscalation(long organizationId, String reference, int level, String message) {
        incidents.recordEscalation(organizationId, reference, level, message);
    }
}
