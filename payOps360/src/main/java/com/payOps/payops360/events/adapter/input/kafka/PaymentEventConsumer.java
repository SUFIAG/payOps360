package com.payOps.payops360.events.adapter.input.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payOps.payops360.events.domain.model.DomainEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka Consumer for Payment Events
 * Processes payment-related events from Kafka topics
 */
@Component
@ConditionalOnProperty(name = "events.publisher.type", havingValue = "kafka")
@RequiredArgsConstructor
@Slf4j
public class PaymentEventConsumer {

    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "payment-events", groupId = "payops360-payment-processor")
    public void consumePaymentEvent(String message) {
        try {
            log.info("📥 Received payment event from Kafka");

            DomainEvent event = objectMapper.readValue(message, DomainEvent.class);

            log.info("Processing payment event: {} | Type: {} | Payment: {}",
                    event.getEventId(),
                    event.getEventType(),
                    event.getAggregateId());

            // Process the event (trigger additional workflows, analytics, etc.)
            processPaymentEvent(event);

        } catch (Exception e) {
            log.error("Failed to process payment event: " + message, e);
            // In production: send to dead letter queue
        }
    }

    private void processPaymentEvent(DomainEvent event) {
        // Business logic based on event type
        switch (event.getEventType()) {
            case "PAYMENT_CREATED":
                log.debug("Payment created: {}", event.getAggregateId());
                // Could trigger: fraud check, provider assignment, etc.
                break;

            case "PAYMENT_FAILED":
                log.warn("Payment failed: {}", event.getAggregateId());
                // Could trigger: alert creation, retry recommendation
                break;

            case "PAYMENT_SETTLED":
                log.info("Payment settled: {}", event.getAggregateId());
                // Could trigger: reconciliation, reporting
                break;

            default:
                log.debug("Unhandled payment event type: {}", event.getEventType());
        }
    }
}

