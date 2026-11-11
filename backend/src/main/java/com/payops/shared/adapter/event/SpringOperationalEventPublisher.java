package com.payops.shared.adapter.event;

import com.payops.shared.application.OperationalEvent;
import com.payops.shared.application.OperationalEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * In-process event bus backed by Spring application events. Listeners use
 * {@code @TransactionalEventListener} to run after the commit.
 */
@Component
@RequiredArgsConstructor
class SpringOperationalEventPublisher implements OperationalEventPublisher {

    private final ApplicationEventPublisher publisher;

    @Override
    public void publish(OperationalEvent event) {
        publisher.publishEvent(event);
    }
}
