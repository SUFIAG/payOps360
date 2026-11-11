package com.payops.intelligence.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payops.analytics.domain.AnalyticsModels;
import com.payops.audit.application.port.in.AuditTrail;
import com.payops.audit.domain.AuditAction;
import com.payops.audit.domain.AuditEntry;
import com.payops.audit.domain.AuditOutcome;
import com.payops.incident.application.port.in.IncidentCommandUseCase;
import com.payops.incident.application.port.in.IncidentQueryUseCase;
import com.payops.incident.application.port.in.IncidentView;
import com.payops.incident.domain.AiAnalysis;
import com.payops.intelligence.application.port.in.InvestigationUseCase;
import com.payops.intelligence.application.port.out.GenerativeAiPort;
import com.payops.intelligence.application.port.out.OperationsDataPort;
import com.payops.intelligence.domain.failure.FailureCategory;
import com.payops.intelligence.domain.failure.FailureKnowledgeBase;
import com.payops.intelligence.domain.routing.RoutingAdvisor;
import com.payops.payment.application.port.in.PaymentQueryUseCase;
import com.payops.payment.application.port.in.PaymentView;
import com.payops.payment.domain.PaymentStatus;
import com.payops.shared.application.Actor;
import com.payops.shared.application.RequestContext;
import com.payops.shared.domain.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * AI root-cause analysis. Evidence is always gathered deterministically; the
 * LLM (when enabled) turns it into a narrative and ranked actions. Without an
 * LLM the built-in reasoning produces the same structure.
 */
@Service
@RequiredArgsConstructor
public class InvestigationService implements InvestigationUseCase {

    private static final String RCA_PROMPT = """
            You are a principal payments site-reliability engineer performing root-cause analysis for an incident
            in PayOps360. You receive structured evidence inside <data> tags. Produce:
            - summary: 2-3 sentences for an executive audience, including business impact.
            - rootCause: the single most likely root cause, citing the evidence (codes, rates, latency, timing).
            - recommendations: 3-5 concrete, ordered actions an operations team can take now.
            - confidence: 0.0-1.0, lower when evidence is thin or contradictory.
            Distinguish provider/network faults from issuer declines, risk controls and integration regressions.
            """ + AiInvoker.GUARDRAILS;

    private static final String PAYMENT_PROMPT = """
            You explain a single payment to a payments operations analyst in under 120 words: what happened,
            why (using the failure-code knowledge and risk factors provided), and the recommended next step.
            Plain sentences, no headings.
            """ + AiInvoker.GUARDRAILS;

    private static final Map<String, Object> RCA_SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of(
                    "summary", Map.of("type", "string"),
                    "rootCause", Map.of("type", "string"),
                    "recommendations", Map.of("type", "array", "items", Map.of("type", "string")),
                    "confidence", Map.of("type", "number")),
            "required", List.of("summary", "rootCause", "recommendations", "confidence"),
            "additionalProperties", false);

    private final OperationsDataPort data;
    private final IncidentCommandUseCase incidentCommands;
    private final AiInvoker ai;
    private final AuditTrail auditTrail;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    // ----------------------------------------------------------- incidents

    @Override
    public IncidentView investigateIncident(Actor actor, String incidentReference, RequestContext context) {
        IncidentQueryUseCase.IncidentDetails details = data.incident(actor.organizationId(), incidentReference)
                .orElseThrow(() -> new NotFoundException("Incident", incidentReference));
        Evidence evidence = gatherEvidence(actor.organizationId(), details);
        AiAnalysis deterministic = deterministicRca(evidence);

        AiAnalysis analysis = ai.generate(actor.organizationId(), actor.userId(),
                        new GenerativeAiPort.Request("incident-investigation", RCA_PROMPT,
                                List.of(new GenerativeAiPort.AiMessage(GenerativeAiPort.Role.USER,
                                        "<data>" + toJson(evidence.asMap()) + "</data>\n\nAnalyse this incident.")),
                                List.of(), RCA_SCHEMA, 4000),
                        GenerativeAiPort.ToolExecutor.none())
                .flatMap(result -> parseRca(result.text(), result.engine()))
                .orElse(deterministic);

        auditTrail.record(new AuditEntry(actor.organizationId(), actor.userId(), actor.email(), AuditAction.AI_INVESTIGATION,
                "INCIDENT", incidentReference, AuditOutcome.SUCCESS, context.ipAddress(), context.userAgent(),
                Map.of("engine", analysis.engine()), clock.instant()));
        return incidentCommands.recordAnalysis(actor.organizationId(), incidentReference, analysis);
    }

    record Evidence(IncidentView incident, AnalyticsModels.ProviderPerformance providerLastHour,
                    AnalyticsModels.ProviderPerformance providerLast24h, List<AnalyticsModels.Breakdown> failures,
                    List<RoutingAdvisor.Ranking> alternatives, List<String> timeline) {

        Map<String, Object> asMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("incident", Map.of("reference", incident.reference(), "title", incident.title(),
                    "severity", incident.severity(), "status", incident.status(), "openedAt", incident.openedAt(),
                    "affectedPayments", incident.affectedPayments(), "affectedAmount", incident.affectedAmount(),
                    "description", String.valueOf(incident.description())));
            map.put("providerLastHour", providerLastHour);
            map.put("providerBaseline24h", providerLast24h);
            map.put("failureCodesLastHour", failures.stream().map(f -> Map.of("code", f.label(), "count", f.count(),
                    "knowledge", FailureKnowledgeBase.lookup(f.label()))).toList());
            map.put("routingAlternatives", alternatives);
            map.put("timeline", timeline);
            return map;
        }
    }

    private Evidence gatherEvidence(long org, IncidentQueryUseCase.IncidentDetails details) {
        IncidentView incident = details.incident();
        String code = incident.providerCode();
        AnalyticsModels.ProviderPerformance lastHour = findProvider(data.providerPerformance(org, Duration.ofHours(1)), code);
        AnalyticsModels.ProviderPerformance lastDay = findProvider(data.providerPerformance(org, Duration.ofHours(24)), code);
        List<AnalyticsModels.Breakdown> failures = data.failureReasons(org, Duration.ofHours(1));
        List<RoutingAdvisor.Ranking> alternatives = InsightsService.routing(data.providerPerformance(org, Duration.ofHours(1)),
                data.providers(org)).stream().filter(r -> !r.code().equals(code)).limit(3).toList();
        List<String> timeline = details.timeline().stream()
                .map(t -> t.occurredAt() + " " + t.kind() + ": " + t.message()).toList();
        return new Evidence(incident, lastHour, lastDay, failures, alternatives, timeline);
    }

    AiAnalysis deterministicRca(Evidence e) {
        List<String> recommendations = new ArrayList<>();
        String rootCause;
        double confidence;
        String providerName = e.providerLastHour() != null ? e.providerLastHour().name() : "the affected provider";

        AnalyticsModels.Breakdown topFailure = e.failures().isEmpty() ? null : e.failures().get(0);
        FailureKnowledgeBase.FailureInsight insight = topFailure == null ? null : FailureKnowledgeBase.lookup(topFailure.label());
        long totalFailures = e.failures().stream().mapToLong(AnalyticsModels.Breakdown::count).sum();
        double dominance = topFailure == null || totalFailures == 0 ? 0 : topFailure.count() * 100.0 / totalFailures;
        boolean latencyDegraded = e.providerLastHour() != null && e.providerLast24h() != null
                && e.providerLast24h().p95LatencyMs() > 0
                && e.providerLastHour().p95LatencyMs() > e.providerLast24h().p95LatencyMs() * 1.5;
        double successDrop = e.providerLastHour() != null && e.providerLast24h() != null
                ? e.providerLast24h().successRate() - e.providerLastHour().successRate() : 0;

        if (insight == null) {
            rootCause = "No failed payments were observed in the last hour; the incident may already be mitigating or was triggered by latency/volume signals.";
            confidence = 0.35;
        } else if (insight.category() == FailureCategory.TECHNICAL) {
            rootCause = String.format("Provider-side technical degradation at %s: %s dominates failures (%.0f%%)%s. Success rate fell %.1f points versus the 24h baseline.",
                    providerName, insight.code(), dominance, latencyDegraded ? " together with a p95 latency spike" : "", Math.max(0, successDrop));
            confidence = dominance >= 50 ? 0.82 : 0.65;
            recommendations.add("Fail over new traffic from " + providerName + " to the top-ranked alternative until error rates normalise.");
            recommendations.add("Open a P1 ticket with " + providerName + " citing " + insight.code() + " volumes and timestamps.");
            recommendations.add("Enable idempotent automatic retries for retryable failures once the provider recovers.");
        } else if (insight.category() == FailureCategory.SOFT_DECLINE || insight.category() == FailureCategory.HARD_DECLINE) {
            rootCause = String.format("Issuer-side declines, not a platform outage: %s accounts for %.0f%% of failures. %s",
                    insight.code(), dominance, insight.meaning());
            confidence = 0.7;
            recommendations.add(insight.operatorAction());
            recommendations.add("Check whether a single issuer BIN range or corridor is concentrated in the declines.");
            recommendations.add("Prompt affected customers with an alternative payment method.");
        } else if (insight.category() == FailureCategory.RISK || insight.category() == FailureCategory.COMPLIANCE) {
            rootCause = String.format("Risk/compliance controls are blocking payments: %s (%.0f%% of failures). This may be a fraud attack or an over-tight rule change.",
                    insight.code(), dominance);
            confidence = 0.68;
            recommendations.add("Review recent risk-rule or screening-list changes with the compliance team.");
            recommendations.add("Sample blocked payments for common attributes (customer, corridor, device) to confirm an attack.");
        } else if (insight.category() == FailureCategory.VALIDATION) {
            rootCause = String.format("Likely integration regression: the provider is rejecting requests as %s (%.0f%% of failures).",
                    insight.code(), dominance);
            confidence = 0.75;
            recommendations.add("Check recent deployments and roll back the payment-integration change if correlated.");
            recommendations.add("Compare a failing request against the provider's API specification.");
        } else {
            rootCause = String.format("Unclassified failure code %s dominates (%.0f%%). Requires provider clarification.", insight.code(), dominance);
            confidence = 0.45;
            recommendations.add("Ask the provider to explain code " + insight.code() + " and map it in the failure knowledge base.");
        }
        if (!e.alternatives().isEmpty() && recommendations.stream().noneMatch(r -> r.startsWith("Fail over"))) {
            RoutingAdvisor.Ranking best = e.alternatives().get(0);
            recommendations.add(String.format("Best routing alternative right now: %s (score %.0f, %.1f%% success).",
                    best.name(), best.score(), best.successRate()));
        }
        recommendations.add("Keep stakeholders updated every 30 minutes until resolution and record learnings in the post-incident review.");

        String summary = String.format("%s incident affecting %d payment(s) worth about %s. %s",
                e.incident().severity(), e.incident().affectedPayments(),
                e.incident().affectedAmount().setScale(0, RoundingMode.HALF_UP).toPlainString(),
                rootCause.split("\\. ")[0] + ".");
        return new AiAnalysis(summary, rootCause, recommendations, BigDecimal.valueOf(confidence),
                ai.deterministicEngine(), clock.instant());
    }

    private Optional<AiAnalysis> parseRca(String json, String engine) {
        try {
            JsonNode node = objectMapper.readTree(json);
            List<String> recs = new ArrayList<>();
            node.path("recommendations").forEach(r -> recs.add(r.asText()));
            double confidence = Math.max(0, Math.min(1, node.path("confidence").asDouble(0.5)));
            if (node.path("summary").asText().isBlank() || node.path("rootCause").asText().isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new AiAnalysis(node.path("summary").asText(), node.path("rootCause").asText(), recs,
                    BigDecimal.valueOf(confidence).setScale(3, RoundingMode.HALF_UP), engine, clock.instant()));
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }

    // ------------------------------------------------------------ payments

    @Override
    public PaymentInsight explainPayment(Actor actor, String paymentReference) {
        PaymentQueryUseCase.PaymentDetails details = data.payment(actor.organizationId(), paymentReference)
                .orElseThrow(() -> new NotFoundException("Payment", paymentReference));
        PaymentView p = details.payment();
        FailureKnowledgeBase.FailureInsight insight = p.failureCode() == null ? null : FailureKnowledgeBase.lookup(p.failureCode());
        boolean failed = p.status() == PaymentStatus.FAILED || p.status() == PaymentStatus.RETRY_FAILED;
        boolean retryRecommended = failed && insight != null && insight.retryable() && details.retryable();

        String suggested = null;
        if (failed && insight != null && insight.category() == FailureCategory.TECHNICAL) {
            suggested = InsightsService.routing(data.providerPerformance(actor.organizationId(), Duration.ofHours(1)),
                            data.providers(actor.organizationId())).stream()
                    .filter(r -> !r.code().equals(p.providerCode())).map(RoutingAdvisor.Ranking::name).findFirst().orElse(null);
        }
        String retryAdvice = !failed ? "No retry needed."
                : retryRecommended ? (suggested != null ? "Retry via " + suggested + " (higher current success rate)." : "Retry is recommended with back-off.")
                : details.retryable() ? "Retry is unlikely to succeed for this failure type." : "Retry limit reached or payment not retryable.";
        String riskSummary = String.format("Risk %d/100 (%s)", p.riskScore(), p.riskLevel());

        String deterministic = narrative(p, insight, retryAdvice);
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("payment", CopilotToolbox.summarise(p));
        facts.put("timeline", details.timeline());
        facts.put("failureKnowledge", insight);
        facts.put("riskFactors", p.riskFactors());
        facts.put("retryAdvice", retryAdvice);
        var llm = ai.generate(actor.organizationId(), actor.userId(),
                new GenerativeAiPort.Request("payment-explanation", PAYMENT_PROMPT,
                        List.of(new GenerativeAiPort.AiMessage(GenerativeAiPort.Role.USER,
                                "<data>" + toJson(facts) + "</data>\n\nExplain this payment.")), List.of(), null, 1500),
                GenerativeAiPort.ToolExecutor.none());

        return new PaymentInsight(p.reference(), p.status().name(), p.failureCode(),
                insight == null ? null : insight.category(), insight == null ? null : insight.meaning(),
                retryRecommended, retryAdvice, insight == null ? null : insight.operatorAction(),
                insight == null ? null : insight.customerMessage(), suggested, riskSummary, p.riskFactors(),
                llm.map(GenerativeAiPort.Result::text).orElse(deterministic),
                llm.map(GenerativeAiPort.Result::engine).orElse(ai.deterministicEngine()), clock.instant());
    }

    private static String narrative(PaymentView p, FailureKnowledgeBase.FailureInsight insight, String retryAdvice) {
        StringBuilder sb = new StringBuilder(String.format("Payment %s of %s %s via %s is %s.", p.reference(),
                p.amount().toPlainString(), p.currency(), p.providerName(), p.status().name().toLowerCase().replace('_', ' ')));
        if (insight != null) {
            sb.append(' ').append("It failed with ").append(insight.code()).append(": ").append(insight.meaning())
                    .append(' ').append(retryAdvice);
        }
        if (p.riskScore() >= 35) {
            sb.append(String.format(" The risk engine scored it %d/100 (%s)%s.", p.riskScore(), p.riskLevel(),
                    p.riskFactors().isEmpty() ? "" : " because: " + String.join("; ", p.riskFactors())));
        }
        if (p.stuck()) {
            sb.append(" It has exceeded its processing time budget and should be reconciled with the provider.");
        }
        return sb.toString();
    }

    private static AnalyticsModels.ProviderPerformance findProvider(List<AnalyticsModels.ProviderPerformance> list, String code) {
        return code == null ? null : list.stream().filter(p -> p.code().equals(code)).findFirst().orElse(null);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
