package com.payOps.payops360.events.adapter.output.local;

import com.payOps.payops360.events.application.port.output.EventPublisher;
import com.payOps/payops360.events.domain.model.DomainEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Local/In-Memory Event Publisher (for development/testing)
 * Does not require Kafka - just logs events
 */
@Component
@ConditionalOnProperty(name = "events.publisher.type", havingValue = "local", matchIfMissing = true)
@Slf4j
public class LocalEventPublisher implements EventPublisher {

    @Override
    public void publish(DomainEvent event) {
        log.info("📤 Event Published: {} | Type: {} | Aggregate: {} | Source: {}",
                event.getEventId(),
                event.getEventType(),
                event.getAggregateType(),
                event.getSource());
        log.debug("Event payload: {}", event.getPayload());
    }

    @Override
    @Async
    public void publishAsync(DomainEvent event) {
        publish(event);
    }
}

