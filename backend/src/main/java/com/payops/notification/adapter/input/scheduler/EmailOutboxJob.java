package com.payops.notification.adapter.input.scheduler;

import com.payops.notification.application.port.input.EmailDeliveryUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class EmailOutboxJob {

    private final EmailDeliveryUseCase delivery;

    @Scheduled(fixedDelayString = "${payops.mail.poll-interval:PT10S}", initialDelayString = "PT15S")
    @SchedulerLock(name = "email-outbox", lockAtMostFor = "PT2M")
    void deliver() {
        int processed = delivery.deliverDue();
        if (processed > 0) {
            log.debug("Processed {} outbox e-mails", processed);
        }
    }
}
