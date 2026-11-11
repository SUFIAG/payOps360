package com.payops.reconciliation.adapter.input.scheduler;

import com.payops.reconciliation.application.port.input.ReconciliationProcessingUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class ReconciliationJobs {

    private final ReconciliationProcessingUseCase processing;

    /** Flags succeeded payments that no settlement reported within the configured age. */
    @Scheduled(fixedDelayString = "${payops.reconciliation.sweep-interval:PT5M}", initialDelayString = "PT1M")
    @SchedulerLock(name = "reconciliation-missing-settlement", lockAtMostFor = "PT10M")
    void flagMissingSettlements() {
        int flagged = processing.flagAgedUnsettledPayments();
        if (flagged > 0) {
            log.info("Flagged {} payment(s) as missing in settlement", flagged);
        }
    }

    /** Re-queues batches whose matching never started or died with the previous instance. */
    @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT45S")
    @SchedulerLock(name = "reconciliation-requeue", lockAtMostFor = "PT2M")
    void requeueStalled() {
        int queued = processing.requeueStalledBatches();
        if (queued > 0) {
            log.info("Re-queued {} settlement batch(es) for matching", queued);
        }
    }
}
