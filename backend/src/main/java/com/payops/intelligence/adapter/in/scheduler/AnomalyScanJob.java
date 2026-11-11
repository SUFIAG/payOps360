package com.payops.intelligence.adapter.in.scheduler;

import com.payops.intelligence.application.port.in.AnomalyScanUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class AnomalyScanJob {

    private final AnomalyScanUseCase scanner;

    @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT45S")
    @SchedulerLock(name = "ai-anomaly-scan", lockAtMostFor = "PT2M")
    void scan() {
        int anomalies = scanner.scan();
        if (anomalies > 0) {
            log.info("AI anomaly scan found {} anomalies", anomalies);
        }
    }
}
