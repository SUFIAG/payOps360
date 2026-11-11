package com.payops.alert.adapter.input.scheduler;

import com.payops.alert.application.port.input.RaiseAlertUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class AlertAutoResolveJob {

    private final RaiseAlertUseCase alerts;

    @Scheduled(fixedDelayString = "PT2M", initialDelayString = "PT1M")
    @SchedulerLock(name = "alert-auto-resolve", lockAtMostFor = "PT2M")
    void autoResolve() {
        int resolved = alerts.autoResolveQuietAlerts();
        if (resolved > 0) {
            log.info("Auto-resolved {} quiet alerts", resolved);
        }
    }
}
