package com.payOps.payops360.ai.adapter.output.context;

import com.payOps.payops360.ai.application.port.output.ContextBuilder;
import com.payOps.payops360.ai.domain.model.InvestigationContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Context Builder Implementation
 * Aggregates data from multiple tables to build investigation context
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ContextBuilderAdapter implements ContextBuilder {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public InvestigationContext buildContextForIncident(UUID incidentId) {
        log.info("Building investigation context for incident: {}", incidentId);

        try {
            // Fetch incident details
            Map<String, Object> incident = jdbcTemplate.queryForMap(
                    "SELECT title, category, severity, impacted_payment_count, related_alert_ids, related_payment_ids FROM incidents WHERE id = ?",
                    incidentId
            );

            String title = (String) incident.get("title");
            String category = (String) incident.get("category");
            String severity = (String) incident.get("severity");
            Integer impactedCount = (Integer) incident.get("impacted_payment_count");

            // Fetch related alert types
            List<String> alertTypes = fetchRelatedAlertTypes(incidentId);

            // Fetch failure categories
            List<String> failureCategories = fetchFailureCategories(incidentId);

            // Fetch recent errors
            List<String> recentErrors = fetchRecentErrors(incidentId);

            // Build provider health map
            Map<String, Object> providerHealth = fetchProviderHealth();

            // Calculate time window
            String timeWindow = calculateTimeWindow(incidentId);

            return InvestigationContext.builder()
                    .incidentId(incidentId.toString())
                    .incidentTitle(title)
                    .incidentCategory(category)
                    .severity(severity)
                    .impactedPaymentCount(impactedCount != null ? impactedCount : 0)
                    .relatedAlertTypes(alertTypes)
                    .failureCategories(failureCategories)
                    .providerHealth(providerHealth)
                    .recentErrors(recentErrors)
                    .systemMetrics(new HashMap<>())
                    .timeWindow(timeWindow)
                    .build();

        } catch (Exception e) {
            log.error("Error building context for incident: " + incidentId, e);

            // Return minimal context on error
            return InvestigationContext.builder()
                    .incidentId(incidentId.toString())
                    .incidentTitle("Incident " + incidentId)
                    .incidentCategory("UNKNOWN")
                    .severity("MEDIUM")
                    .impactedPaymentCount(0)
                    .relatedAlertTypes(new ArrayList<>())
                    .failureCategories(new ArrayList<>())
                    .providerHealth(new HashMap<>())
                    .recentErrors(new ArrayList<>())
                    .systemMetrics(new HashMap<>())
                    .timeWindow("Unknown")
                    .build();
        }
    }

    private List<String> fetchRelatedAlertTypes(UUID incidentId) {
        try {
            String sql = """
                    SELECT DISTINCT a.alert_type
                    FROM alerts a
                    INNER JOIN incidents i ON i.id = ?
                    WHERE i.related_alert_ids::jsonb @> to_jsonb(a.id::text)
                    LIMIT 10
                    """;

            return jdbcTemplate.queryForList(sql, String.class, incidentId);
        } catch (Exception e) {
            log.warn("Could not fetch alert types for incident: " + incidentId, e);
            return new ArrayList<>();
        }
    }

    private List<String> fetchFailureCategories(UUID incidentId) {
        try {
            String sql = """
                    SELECT DISTINCT fc.category
                    FROM failure_classifications fc
                    INNER JOIN incidents i ON i.id = ?
                    WHERE i.related_payment_ids::jsonb @> to_jsonb(fc.payment_id::text)
                    LIMIT 10
                    """;

            return jdbcTemplate.queryForList(sql, String.class, incidentId);
        } catch (Exception e) {
            log.warn("Could not fetch failure categories for incident: " + incidentId, e);
            return new ArrayList<>();
        }
    }

    private List<String> fetchRecentErrors(UUID incidentId) {
        try {
            String sql = """
                    SELECT DISTINCT fc.error_message
                    FROM failure_classifications fc
                    INNER JOIN incidents i ON i.id = ?
                    WHERE i.related_payment_ids::jsonb @> to_jsonb(fc.payment_id::text)
                    AND fc.error_message IS NOT NULL
                    ORDER BY fc.classified_at DESC
                    LIMIT 5
                    """;

            return jdbcTemplate.queryForList(sql, String.class, incidentId);
        } catch (Exception e) {
            log.warn("Could not fetch recent errors for incident: " + incidentId, e);
            return new ArrayList<>();
        }
    }

    private Map<String, Object> fetchProviderHealth() {
        try {
            String sql = """
                    SELECT provider_id, success_rate, average_latency_ms
                    FROM provider_health_snapshots
                    WHERE recorded_at > NOW() - INTERVAL '1 hour'
                    ORDER BY recorded_at DESC
                    LIMIT 5
                    """;

            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql);
            Map<String, Object> health = new HashMap<>();

            for (Map<String, Object> row : results) {
                String providerId = (String) row.get("provider_id");
                health.put(providerId + "_success_rate", row.get("success_rate"));
                health.put(providerId + "_latency", row.get("average_latency_ms"));
            }

            return health;
        } catch (Exception e) {
            log.warn("Could not fetch provider health", e);
            return new HashMap<>();
        }
    }

    private String calculateTimeWindow(UUID incidentId) {
        try {
            Map<String, Object> times = jdbcTemplate.queryForMap(
                    "SELECT detected_at, resolved_at FROM incidents WHERE id = ?",
                    incidentId
            );

            if (times.get("resolved_at") != null) {
                return "Resolved incident";
            }

            return "Active incident (detected recently)";
        } catch (Exception e) {
            return "Unknown time window";
        }
    }
}

