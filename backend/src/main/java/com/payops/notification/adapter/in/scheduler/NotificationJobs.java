package com.payops.notification.adapter.in.scheduler;

import com.payops.notification.application.port.in.EscalationUseCase;
import com.payops.notification.application.port.in.NotificationDispatchUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drains the notification delivery outbox and runs the escalation engine,
 * each on exactly one replica.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class NotificationJobs {

    private final NotificationDispatchUseCase dispatch;
    private final EscalationUseCase escalation;

    @Scheduled(fixedDelayString = "${payops.notifications.poll-interval:PT10S}", initialDelayString = "PT20S")
    @SchedulerLock(name = "notification-deliveries", lockAtMostFor = "PT5M")
    void deliver() {
        int processed = dispatch.deliverDue();
        if (processed > 0) {
            log.debug("Processed {} notification deliveries", processed);
        }
    }

    @Scheduled(fixedRateString = "${payops.notifications.escalation-interval:PT1M}", initialDelayString = "PT30S")
    @SchedulerLock(name = "notification-escalations", lockAtMostFor = "PT2M", lockAtLeastFor = "PT20S")
    void escalate() {
        try {
            int steps = escalation.escalateDue();
            if (steps > 0) {
                log.info("Took {} escalation step(s)", steps);
            }
        } catch (RuntimeException e) {
            // e.g. an optimistic-lock conflict with an operator acknowledging; retried next minute.
            log.warn("Escalation run failed, retrying next minute: {}", e.getMessage());
        }
    }
}
