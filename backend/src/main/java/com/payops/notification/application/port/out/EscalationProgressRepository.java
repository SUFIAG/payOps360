package com.payops.notification.application.port.out;

import java.time.Instant;
import java.util.Optional;

public interface EscalationProgressRepository {

    Optional<Progress> find(long organizationId, String subjectType, String subjectReference);

    void save(long organizationId, String subjectType, String subjectReference, long policyId, int level, Instant at);

    record Progress(long policyId, int level, Instant lastEscalatedAt) {
    }
}
