package com.payops.intelligence.adapter.out.ai;

import com.payops.intelligence.application.port.out.GenerativeAiPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Slf4j
@Configuration
@EnableConfigurationProperties(GenerativeAiConfig.AiProperties.class)
class GenerativeAiConfig {

    @Bean
    GenerativeAiPort generativeAiPort(AiProperties properties) {
        if (properties.anthropicApiKey() == null || properties.anthropicApiKey().isBlank()) {
            log.info("ANTHROPIC_API_KEY not set: AI features run on the deterministic PayOps360 insight engine");
            return new DisabledGenerativeAiAdapter();
        }
        log.info("Generative AI enabled with model {}", properties.model());
        return new ClaudeGenerativeAiAdapter(properties.anthropicApiKey(), properties.model(), properties.maxTokens(),
                properties.timeout());
    }

    @ConfigurationProperties(prefix = "payops.ai")
    record AiProperties(String anthropicApiKey, String model, long maxTokens, Duration timeout) {
    }
}
