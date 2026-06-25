package com.payOps.payops360.events.application.port.output;

import com.payOps.payops360.events.domain.model.DomainEvent;

/**
 * Output Port: Event Publisher
 * Abstraction for publishing events to message broker (Kafka, RabbitMQ, etc.)
 */
public interface EventPublisher {
    void publish(DomainEvent event);
    void publishAsync(DomainEvent event);
}

