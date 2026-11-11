package com.payops.provider.adapter.in.scheduler;

import com.payops.provider.application.port.in.ProviderHealthUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class ProviderHealthJob {

    private final ProviderHealthUseCase health;

    @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT20S")
    @SchedulerLock(name = "provider-health", lockAtMostFor = "PT2M")
    void capture() {
        int captured = health.captureSnapshots();
        log.debug("Captured {} provider health snapshots", captured);
    }
}
