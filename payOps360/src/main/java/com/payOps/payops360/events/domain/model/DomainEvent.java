package com.payOps.payops360.events.domain.model;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Domain Event (Pure Domain - No Framework Dependencies)
 * Base class for all domain events in the system
 */
@Getter
@Builder
public class DomainEvent {
    private final UUID eventId;
    private final String eventType;
    private final String aggregateId;
    private final String aggregateType;
    private final Map<String, Object> payload;
    private final Instant occurredAt;
    private final String source;

    public boolean isPaymentEvent() {
        return "PAYMENT".equals(aggregateType);
    }

    public boolean isAlertEvent() {
        return "ALERT".equals(aggregateType);
    }

    public boolean isIncidentEvent() {
        return "INCIDENT".equals(aggregateType);
    }
}

