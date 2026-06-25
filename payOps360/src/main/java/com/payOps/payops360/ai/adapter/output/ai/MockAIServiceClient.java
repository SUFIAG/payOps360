package com.payOps.payops360.ai.adapter.output.ai;

import com.payOps.payops360.ai.application.port.output.AIServiceClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Mock AI Service Client for testing (no external API calls)
 * Can be replaced with real OpenAI/Claude client when API keys are configured
 */
@Component
@ConditionalOnProperty(name = "ai.service.provider", havingValue = "mock", matchIfMissing = true)
@Slf4j
public class MockAIServiceClient implements AIServiceClient {

    @Override
    public String generateResponse(String prompt, String model) {
        log.info("MockAIServiceClient generating response for model: {}", model);

        // Simulate AI processing delay
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Generate mock response based on prompt content
        return generateMockResponse(prompt);
    }

    private String generateMockResponse(String prompt) {
        // Parse prompt to understand incident
        boolean isProviderIssue = prompt.toLowerCase().contains("provider");
        boolean isNetworkIssue = prompt.toLowerCase().contains("network") || prompt.toLowerCase().contains("timeout");
        boolean isHighImpact = prompt.contains("Severity: CRITICAL") || prompt.contains("Severity: HIGH");

        StringBuilder response = new StringBuilder();

        // ROOT CAUSE
        response.append("ROOT CAUSE: ");
        if (isProviderIssue) {
            response.append("Payment provider experiencing systemic issues. Analysis indicates degraded performance across multiple transactions, ");
            response.append("likely due to capacity constraints or internal infrastructure problems at the payment gateway.");
        } else if (isNetworkIssue) {
            response.append("Network connectivity issues detected. Multiple timeout errors suggest intermittent network latency or ");
            response.append("unstable connection between payment processing system and external services.");
        } else {
            response.append("Multiple correlated payment failures detected. Pattern suggests a configuration issue or ");
            response.append("temporary system overload affecting transaction processing pipeline.");
        }
        response.append("\n\n");

        // REASONING
        response.append("REASONING:\n");
        response.append("1. Alert correlation shows multiple failures within a short time window, indicating systemic rather than isolated issue\n");
        response.append("2. Failure pattern analysis reveals consistent error types across different transactions\n");
        response.append("3. Provider health metrics show degradation coinciding with incident timeline\n");
        response.append("4. Historical data comparison suggests this is an anomalous event requiring immediate attention\n\n");

        // RECOMMENDATIONS
        response.append("RECOMMENDATIONS:\n");
        if (isHighImpact) {
            response.append("1. Implement immediate provider fallback to alternative payment gateway to restore service\n");
            response.append("2. Enable circuit breaker pattern to prevent cascading failures\n");
            response.append("3. Contact primary provider support team for incident escalation and status updates\n");
            response.append("4. Increase monitoring frequency to detect similar patterns proactively\n");
            response.append("5. Review and update retry policies to handle provider degradation more gracefully\n\n");
        } else {
            response.append("1. Monitor provider health metrics closely for next 2-4 hours\n");
            response.append("2. Enable enhanced logging for affected payment routes\n");
            response.append("3. Prepare fallback provider configuration for quick activation if needed\n");
            response.append("4. Review recent deployments or configuration changes that may correlate with failures\n\n");
        }

        // PREVENTION
        response.append("PREVENTION:\n");
        response.append("- Implement multi-provider routing with automatic failover capabilities\n");
        response.append("- Enhance anomaly detection algorithms to catch similar issues earlier\n");
        response.append("- Establish SLA monitoring with automated alerts for provider performance degradation\n");
        response.append("- Create runbooks for common incident scenarios to reduce response time\n");
        response.append("- Conduct regular load testing to identify system capacity limits\n");

        return response.toString();
    }
}

