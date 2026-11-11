package com.payops.incident.application.port.input;

import com.payops.incident.domain.IncidentStatus;
import com.payops.incident.domain.TimelineEntry;
import com.payops.shared.domain.PageQuery;
import com.payops.shared.domain.PageResult;

import java.util.List;

public interface IncidentQueryUseCase {

    PageResult<IncidentView> list(long organizationId, IncidentStatus status, PageQuery page);

    IncidentDetails get(long organizationId, String reference);

    Summary summary(long organizationId);

    /** Active (unresolved) incidents, most severe first. */
    List<IncidentView> active(long organizationId);

    record IncidentDetails(IncidentView incident, List<TimelineEntry> timeline) {
    }

    /**
     * @param meanMinutesToAcknowledge MTTA over incidents resolved in the last 30 days
     * @param meanMinutesToResolve     MTTR over incidents resolved in the last 30 days
     * @param activeSlaBreaches        unresolved incidents with a breached acknowledge or resolve SLA
     * @param slaAttainmentPercent     share of incidents resolved in the last 30 days without any SLA breach
     */
    record Summary(long open, long acknowledged, long investigating, long resolvedLast30Days,
                   Double meanMinutesToAcknowledge, Double meanMinutesToResolve, long activeSlaBreaches,
                   Double slaAttainmentPercent) {
    }
}
