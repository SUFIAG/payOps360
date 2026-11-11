package com.payops.incident.adapter.input.scheduler;

import com.payops.incident.application.port.input.IncidentSlaUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Flags incidents whose acknowledge / resolve deadline passed. Runs every
 * minute on exactly one replica.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class IncidentSlaJob {

    private final IncidentSlaUseCase sla;

    @Scheduled(fixedRateString = "${payops.incidents.sla-check-interval:PT1M}", initialDelayString = "PT20S")
    @SchedulerLock(name = "incident-sla-monitor", lockAtMostFor = "PT2M", lockAtLeastFor = "PT20S")
    void flagBreaches() {
        try {
            int flagged = sla.flagSlaBreaches();
            if (flagged > 0) {
                log.info("Flagged SLA breaches on {} incident(s)", flagged);
            }
        } catch (RuntimeException e) {
            // Optimistic-lock conflicts with operators are retried on the next run.
            log.warn("SLA monitor run failed, retrying next minute: {}", e.getMessage());
        }
    }
}
