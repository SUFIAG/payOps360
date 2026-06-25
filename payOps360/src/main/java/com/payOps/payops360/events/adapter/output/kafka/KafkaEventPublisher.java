package com.payOps.payops360.events.adapter.output.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payOps.payops360.events.application.port.output.EventPublisher;
import com.payOps.payops360.events.domain.model.DomainEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Kafka Event Publisher Implementation
 * Publishes domain events to Kafka topics
 */
@Component
@ConditionalOnProperty(name = "events.publisher.type", havingValue = "kafka", matchIfMissing = false)
@RequiredArgsConstructor
@Slf4j
public class KafkaEventPublisher implements EventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    private static final String PAYMENT_EVENTS_TOPIC = "payment-events";
    private static final String ALERT_EVENTS_TOPIC = "alert-events";
    private static final String INCIDENT_EVENTS_TOPIC = "incident-events";

    @Override
    public void publish(DomainEvent event) {
        try {
            String topic = determineTopic(event);
            String key = event.getAggregateId();
            String payload = objectMapper.writeValueAsString(event);

            CompletableFuture<SendResult<String, String>> future = kafkaTemplate.send(topic, key, payload);

            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    log.info("Event published successfully: {} to topic: {}", event.getEventId(), topic);
                } else {
                    log.error("Failed to publish event: " + event.getEventId(), ex);
                }
            });

        } catch (Exception e) {
            log.error("Error publishing event: " + event.getEventId(), e);
            throw new RuntimeException("Failed to publish event", e);
        }
    }

    @Override
    @Async
    public void publishAsync(DomainEvent event) {
        publish(event);
    }

    private String determineTopic(DomainEvent event) {
        if (event.isPaymentEvent()) {
            return PAYMENT_EVENTS_TOPIC;
        } else if (event.isAlertEvent()) {
            return ALERT_EVENTS_TOPIC;
        } else if (event.isIncidentEvent()) {
            return INCIDENT_EVENTS_TOPIC;
        }
        return "domain-events"; // Default topic
    }
}

