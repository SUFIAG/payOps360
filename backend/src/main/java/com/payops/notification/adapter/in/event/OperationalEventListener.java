package com.payops.notification.adapter.in.event;

import com.payops.notification.application.port.in.NotificationDispatchUseCase;
import com.payops.shared.application.OperationalEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Routes operational events to notification channels once the business
 * transaction that produced them has committed (or immediately when there was
 * none, e.g. scheduler runs). Routing failures never affect the publisher.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class OperationalEventListener {

    private final NotificationDispatchUseCase dispatch;

    @TransactionalEventListener(fallbackExecution = true)
    void on(OperationalEvent event) {
        try {
            int queued = dispatch.route(event);
            if (queued > 0) {
                log.debug("Queued {} notification(s) for {} {}", queued, event.type(), event.subjectReference());
            }
        } catch (RuntimeException e) {
            log.warn("Notification routing failed for {} {}: {}", event.type(), event.subjectReference(), e.getMessage());
        }
    }
}
