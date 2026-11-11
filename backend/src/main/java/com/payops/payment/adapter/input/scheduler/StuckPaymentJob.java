package com.payops.payment.adapter.input.scheduler;

import com.payops.payment.application.port.input.StuckPaymentDetectionUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class StuckPaymentJob {

    private final StuckPaymentDetectionUseCase detection;

    @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT30S")
    @SchedulerLock(name = "stuck-payments", lockAtMostFor = "PT2M")
    void detect() {
        int flagged = detection.detectStuckPayments();
        if (flagged > 0) {
            log.info("Flagged {} stuck payments", flagged);
        }
    }
}
