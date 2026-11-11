package com.payops.incident.application.port.in;

import com.payops.incident.domain.SlaPolicy;
import com.payops.shared.application.Actor;
import com.payops.shared.application.RequestContext;
import com.payops.shared.domain.Severity;

import java.util.List;
import java.util.Map;

/**
 * Incident SLA targets per tenant and the breach monitor.
 */
public interface IncidentSlaUseCase {

    List<SlaTargetView> slaTargets(long organizationId);

    List<SlaTargetView> updateSlaTargets(Actor actor, Map<Severity, SlaPolicy.Target> targets, RequestContext context);

    /** Flags overdue SLAs across all tenants. Invoked by the scheduler on one replica. */
    int flagSlaBreaches();

    /**
     * @param customized false when the built-in default applies
     */
    record SlaTargetView(Severity severity, int ackMinutes, int resolveMinutes, boolean customized) {
    }
}
