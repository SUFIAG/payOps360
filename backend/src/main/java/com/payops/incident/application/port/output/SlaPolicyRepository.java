package com.payops.incident.application.port.output;

import com.payops.incident.domain.SlaPolicy;
import com.payops.shared.domain.Severity;

import java.time.Instant;
import java.util.Map;

public interface SlaPolicyRepository {

    /** Tenant overrides only; severities without a row use the defaults. */
    Map<Severity, SlaPolicy.Target> overrides(long organizationId);

    void save(long organizationId, Map<Severity, SlaPolicy.Target> targets, String actor, Instant now);
}
