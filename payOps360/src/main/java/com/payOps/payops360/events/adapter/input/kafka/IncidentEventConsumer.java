package com.payOps.payops360.events.adapter.input.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payOps.payops360.events.domain.model.DomainEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka Consumer for Incident Events
 * Processes incident-related events from Kafka topics
 */
@Component
@ConditionalOnProperty(name = "events.publisher.type", havingValue = "kafka")
@RequiredArgsConstructor
@Slf4j
public class IncidentEventConsumer {

    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "incident-events", groupId = "payops360-incident-analyzer")
    public void consumeIncidentEvent(String message) {
        try {
            log.info("📥 Received incident event from Kafka");

            DomainEvent event = objectMapper.readValue(message, DomainEvent.class);

            log.info("Processing incident event: {} | Type: {} | Incident: {}",
                    event.getEventId(),
                    event.getEventType(),
                    event.getAggregateId());

            // Process the event
            processIncidentEvent(event);

        } catch (Exception e) {
            log.error("Failed to process incident event: " + message, e);
        }
    }

    private void processIncidentEvent(DomainEvent event) {
        switch (event.getEventType()) {
            case "INCIDENT_CREATED":
                log.error("⚠️ New incident detected: {}", event.getAggregateId());
                // Could trigger: AI investigation, team notification
                break;

            case "INCIDENT_RESOLVED":
                log.info("✅ Incident resolved: {}", event.getAggregateId());
                // Could trigger: post-mortem, metrics update
                break;

            case "INCIDENT_ESCALATED":
                log.error("🚨 Incident escalated: {}", event.getAggregateId());
                // Could trigger: management notification, emergency response
                break;

            default:
                log.debug("Unhandled incident event type: {}", event.getEventType());
        }
    }
}

