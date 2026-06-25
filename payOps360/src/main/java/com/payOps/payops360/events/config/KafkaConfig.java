package com.payOps/payops360.events.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka Configuration
 * Creates required topics for event streaming
 */
@Configuration
@ConditionalOnProperty(name = "events.publisher.type", havingValue = "kafka")
public class KafkaConfig {

    @Bean
    public NewTopic paymentEventsTopic() {
        return TopicBuilder.name("payment-events")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic alertEventsTopic() {
        return TopicBuilder.name("alert-events")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic incidentEventsTopic() {
        return TopicBuilder.name("incident-events")
                .partitions(3)
                .replicas(1)
                .build();
    }
}

