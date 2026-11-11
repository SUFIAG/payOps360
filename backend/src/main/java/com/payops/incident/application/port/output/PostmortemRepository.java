package com.payops.incident.application.port.output;

import com.payops.incident.domain.Postmortem;

import java.util.Optional;

public interface PostmortemRepository {

    Optional<Postmortem> findByIncident(long organizationId, long incidentId);

    Postmortem save(Postmortem postmortem);
}
