package com.payOps.payops360.events.adapter.input.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payOps.payops360.events.domain.model.DomainEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka Consumer for Alert Events
 * Processes alert-related events from Kafka topics
 */
@Component
@ConditionalOnProperty(name = "events.publisher.type", havingValue = "kafka")
@RequiredArgsConstructor
@Slf4j
public class AlertEventConsumer {

    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "alert-events", groupId = "payops360-alert-correlator")
    public void consumeAlertEvent(String message) {
        try {
            log.info("📥 Received alert event from Kafka");

            DomainEvent event = objectMapper.readValue(message, DomainEvent.class);

            log.info("Processing alert event: {} | Type: {} | Alert: {}",
                    event.getEventId(),
                    event.getEventType(),
                    event.getAggregateId());

            // Process the event
            processAlertEvent(event);

        } catch (Exception e) {
            log.error("Failed to process alert event: " + message, e);
        }
    }

    private void processAlertEvent(DomainEvent event) {
        switch (event.getEventType()) {
            case "ALERT_CREATED":
                log.warn("New alert created: {}", event.getAggregateId());
                // Could trigger: incident correlation, notification
                break;

            case "ALERT_RESOLVED":
                log.info("Alert resolved: {}", event.getAggregateId());
                // Could trigger: incident resolution check
                break;

            case "ALERT_ESCALATED":
                log.error("Alert escalated: {}", event.getAggregateId());
                // Could trigger: urgent notification, paging
                break;

            default:
                log.debug("Unhandled alert event type: {}", event.getEventType());
        }
    }
}

