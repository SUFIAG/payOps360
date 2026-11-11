package com.payops.intelligence.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payops.analytics.domain.AnalyticsModels;
import com.payops.intelligence.application.port.out.GenerativeAiPort.AiTool;
import com.payops.intelligence.application.port.out.OperationsDataPort;
import com.payops.intelligence.domain.failure.FailureKnowledgeBase;
import com.payops.intelligence.domain.forecast.VolumeForecaster;
import com.payops.intelligence.domain.routing.RoutingAdvisor;
import com.payops.payment.application.port.in.PaymentView;
import com.payops.payment.domain.PaymentSearchCriteria;
import com.payops.payment.domain.PaymentStatus;
import com.payops.payment.domain.RiskAssessment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Read-only, tenant-scoped tools exposed to the copilot. Results never contain
 * full customer identifiers: customer references are masked before they leave
 * the platform.
 */
@Component
@RequiredArgsConstructor
class CopilotToolbox {

    private static final int MAX_WINDOW_HOURS = 24 * 30;

    private final OperationsDataPort data;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    List<AiTool> definitions() {
        return List.of(
                tool("get_kpis", "Headline KPIs (volume in USD, transactions, success rate, p95 latency, revenue at risk, high-risk and stuck payments) for the last N hours compared with the previous period.",
                        Map.of("window_hours", intProp("Look-back window in hours, 1-720"))),
                tool("get_provider_performance", "Per-provider success rate, latency, volume, SLA and cost for the last N hours.",
                        Map.of("window_hours", intProp("Look-back window in hours, 1-720"))),
                tool("get_failure_reasons", "Top payment failure codes with counts, volume and what each code means.",
                        Map.of("window_hours", intProp("Look-back window in hours, 1-720"))),
                tool("get_active_incidents", "Open incidents with severity, impact and any AI analysis.", Map.of()),
                tool("get_alert_summary", "Counts of open and acknowledged alerts by severity.", Map.of()),
                tool("search_payments", "Find payments matching filters. Returns at most 20 rows.", Map.of(
                        "status", Map.of("type", "string", "description", "One of " + List.of(PaymentStatus.values())),
                        "provider", Map.of("type", "string", "description", "Provider code"),
                        "min_amount", Map.of("type", "number", "description", "Minimum amount"),
                        "currency", Map.of("type", "string", "description", "ISO currency code"),
                        "risk_level", Map.of("type", "string", "description", "Minimum risk level LOW, MEDIUM, HIGH or CRITICAL"),
                        "window_hours", intProp("Look-back window in hours, 1-720"))),
                tool("get_payment", "Full details and lifecycle timeline of one payment by reference (e.g. PAY-XXXXXXXXXXXX).",
                        Map.of("reference", Map.of("type", "string", "description", "Payment reference")), List.of("reference")),
                tool("explain_failure_code", "Explain a provider failure code: category, retryability and recommended action.",
                        Map.of("code", Map.of("type", "string", "description", "Failure code")), List.of("code")),
                tool("forecast_volume", "Hourly payment-count forecast with an 80% prediction band.",
                        Map.of("hours", intProp("Forecast horizon in hours, 1-24"))),
                tool("recommend_routing", "Rank providers for routing by success rate, latency and cost.", Map.of()),
                tool("get_corridors", "Payment volume and success rate by destination country.",
                        Map.of("window_hours", intProp("Look-back window in hours, 1-720"))));
    }

    String execute(long organizationId, String name, Map<String, Object> input) {
        Object result = switch (name) {
            case "get_kpis" -> kpis(organizationId, hours(input, 24));
            case "get_provider_performance" -> data.providerPerformance(organizationId, Duration.ofHours(hours(input, 24)));
            case "get_failure_reasons" -> failureReasons(organizationId, hours(input, 24));
            case "get_active_incidents" -> data.activeIncidents(organizationId);
            case "get_alert_summary" -> data.alertSummary(organizationId);
            case "search_payments" -> searchPayments(organizationId, input);
            case "get_payment" -> payment(organizationId, String.valueOf(input.getOrDefault("reference", "")));
            case "explain_failure_code" -> FailureKnowledgeBase.lookup(String.valueOf(input.getOrDefault("code", "")));
            case "forecast_volume" -> forecast(organizationId, Math.max(1, Math.min(24, hours(input, 6, "hours"))));
            case "recommend_routing" -> routing(organizationId);
            case "get_corridors" -> data.corridors(organizationId, Duration.ofHours(hours(input, 24)));
            default -> Map.of("error", "unknown tool " + name);
        };
        return toJson(result);
    }

    // ---------------------------------------------------------------- tools

    AnalyticsModels.Overview kpis(long organizationId, int hours) {
        return data.overview(organizationId, Duration.ofHours(hours));
    }

    List<Map<String, Object>> failureReasons(long organizationId, int hours) {
        return data.failureReasons(organizationId, Duration.ofHours(hours)).stream().map(b -> {
            FailureKnowledgeBase.FailureInsight insight = FailureKnowledgeBase.lookup(b.label());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("code", b.label());
            row.put("count", b.count());
            row.put("volumeUsd", b.volumeUsd());
            row.put("category", insight.category());
            row.put("retryable", insight.retryable());
            row.put("meaning", insight.meaning());
            return row;
        }).toList();
    }

    List<Map<String, Object>> searchPayments(long organizationId, Map<String, Object> input) {
        Set<PaymentStatus> statuses = input.get("status") == null ? Set.of()
                : Set.of(PaymentStatus.valueOf(String.valueOf(input.get("status")).toUpperCase(Locale.ROOT)));
        Long providerId = input.get("provider") == null ? null
                : data.providerId(organizationId, String.valueOf(input.get("provider")).toLowerCase(Locale.ROOT)).orElse(-1L);
        RiskAssessment.RiskLevel risk = input.get("risk_level") == null ? null
                : RiskAssessment.RiskLevel.valueOf(String.valueOf(input.get("risk_level")).toUpperCase(Locale.ROOT));
        BigDecimal minAmount = input.get("min_amount") == null ? null : new BigDecimal(String.valueOf(input.get("min_amount")));
        String currency = input.get("currency") == null ? null : String.valueOf(input.get("currency"));
        PaymentSearchCriteria criteria = new PaymentSearchCriteria(statuses, providerId, null, risk, currency, minAmount,
                null, null, null, null, null, clock.instant().minus(Duration.ofHours(hours(input, 24))), null);
        return data.searchPayments(organizationId, criteria, 20).stream().map(CopilotToolbox::summarise).toList();
    }

    Object payment(long organizationId, String reference) {
        return data.payment(organizationId, reference.trim().toUpperCase(Locale.ROOT))
                .<Object>map(d -> Map.of(
                        "payment", summarise(d.payment()),
                        "riskFactors", d.payment().riskFactors(),
                        "timeline", d.timeline(),
                        "retryable", d.retryable(),
                        "failureInsight", FailureKnowledgeBase.lookup(d.payment().failureCode())))
                .orElse(Map.of("error", "payment not found"));
    }

    List<VolumeForecaster.Point> forecast(long organizationId, int hours) {
        List<AnalyticsModels.TimeBucket> hourly = data.hourlySeries(organizationId, Duration.ofHours(72));
        if (hourly.isEmpty()) {
            return List.of();
        }
        return VolumeForecaster.forecast(hourly.stream().map(b -> (double) b.total()).toList(),
                hourly.get(hourly.size() - 1).bucket(), hours);
    }

    List<RoutingAdvisor.Ranking> routing(long organizationId) {
        return InsightsService.routing(data.providerPerformance(organizationId, Duration.ofHours(1)),
                data.providers(organizationId));
    }

    // --------------------------------------------------------------- helpers

    static Map<String, Object> summarise(PaymentView p) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("reference", p.reference());
        row.put("amount", p.amount());
        row.put("currency", p.currency());
        row.put("provider", p.providerCode());
        row.put("method", p.method());
        row.put("country", p.country());
        row.put("status", p.status());
        row.put("failureCode", p.failureCode());
        row.put("riskScore", p.riskScore());
        row.put("riskLevel", p.riskLevel());
        row.put("customer", maskCustomer(p.customerRef()));
        row.put("createdAt", p.createdAt());
        return row;
    }

    static String maskCustomer(String customerRef) {
        if (customerRef == null || customerRef.length() <= 4) {
            return "***";
        }
        return "***" + customerRef.substring(customerRef.length() - 4);
    }

    private static int hours(Map<String, Object> input, int fallback) {
        return hours(input, fallback, "window_hours");
    }

    private static int hours(Map<String, Object> input, int fallback, String key) {
        Object value = input.get(key);
        if (value == null) {
            return fallback;
        }
        try {
            int parsed = (int) Double.parseDouble(String.valueOf(value));
            return Math.max(1, Math.min(MAX_WINDOW_HOURS, parsed));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "{\"error\":\"serialisation failed\"}";
        }
    }

    private static AiTool tool(String name, String description, Map<String, Object> properties) {
        return tool(name, description, properties, List.of());
    }

    private static AiTool tool(String name, String description, Map<String, Object> properties, List<String> required) {
        return new AiTool(name, description, Map.of("type", "object", "properties", properties, "required", required));
    }

    private static Map<String, Object> intProp(String description) {
        return Map.of("type", "integer", "description", description);
    }
}
