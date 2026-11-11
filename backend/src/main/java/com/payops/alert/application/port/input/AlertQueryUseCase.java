package com.payops.alert.application.port.input;

import com.payops.alert.domain.AlertStatus;
import com.payops.shared.domain.PageQuery;
import com.payops.shared.domain.PageResult;
import com.payops.shared.domain.Severity;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public interface AlertQueryUseCase {

    PageResult<AlertView> list(long organizationId, AlertStatus status, Severity severity, PageQuery page);

    AlertView get(long organizationId, String reference);

    Summary summary(long organizationId);

    /** System-level, across tenants: unacknowledged alerts of at least {@code minSeverity}, oldest first. */
    List<AwaitingAcknowledgement> awaitingAcknowledgement(Severity minSeverity, int limit);

    record Summary(long open, long acknowledged, long resolvedLast24h, Map<Severity, Long> openBySeverity) {
    }

    record AwaitingAcknowledgement(long organizationId, String reference, Severity severity, String title,
                                   String providerCode, Instant detectedAt) {
    }
}
