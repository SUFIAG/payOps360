package com.payops.intelligence.application.service;

import com.payops.analytics.domain.AnalyticsModels;
import com.payops.audit.application.port.input.AuditTrail;
import com.payops.audit.domain.AuditAction;
import com.payops.audit.domain.AuditEntry;
import com.payops.audit.domain.AuditOutcome;
import com.payops.incident.application.port.input.IncidentView;
import com.payops.intelligence.application.port.input.CopilotUseCase;
import com.payops.intelligence.application.port.output.GenerativeAiPort;
import com.payops.intelligence.application.port.output.OperationsDataPort;
import com.payops.intelligence.domain.failure.FailureKnowledgeBase;
import com.payops.intelligence.domain.forecast.VolumeForecaster;
import com.payops.intelligence.domain.routing.RoutingAdvisor;
import com.payops.shared.application.Actor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PayOps Copilot. With a configured LLM it reasons over live data through
 * read-only tools; otherwise an intent-driven engine answers the same
 * questions from the same tools.
 */
@Service
@RequiredArgsConstructor
public class CopilotService implements CopilotUseCase {

    private static final int MAX_QUESTION = 2000;
    private static final int MAX_HISTORY = 10;
    private static final Pattern PAYMENT_REF = Pattern.compile("PAY-[A-Z0-9]{12}", Pattern.CASE_INSENSITIVE);
    private static final Pattern HOURS = Pattern.compile("(\\d{1,3})\\s*(h|hr|hrs|hour|hours)\\b");
    private static final DateTimeFormatter HOUR_FORMAT = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneOffset.UTC);

    private static final String SYSTEM_PROMPT = """
            You are PayOps Copilot, the AI operations analyst inside PayOps360, a payment operations command center
            used by payment service providers, remittance companies and fintech operations teams.

            How to work:
            - Answer from live data. Call the tools you need before answering; never invent numbers.
            - Lead with the answer in one or two sentences, then supporting facts. Use short markdown sections,
              bullet points and tables only where they help scanning. Keep answers under 250 words unless asked.
            - Quote figures with units (USD, %, ms) and the time window they cover.
            - When something is wrong, state the likely cause, the business impact and the next best action.
            - Monetary values from tools are in USD (indicative FX conversion).
            - Customer identifiers are masked by design; do not try to unmask them.
            """ + AiInvoker.GUARDRAILS;

    private final AiInvoker ai;
    private final CopilotToolbox toolbox;
    private final OperationsDataPort data;
    private final AuditTrail auditTrail;
    private final Clock clock;

    @Override
    public CopilotReply ask(Actor actor, String question, List<Turn> history) {
        String q = question == null ? "" : question.strip();
        if (q.length() > MAX_QUESTION) {
            q = q.substring(0, MAX_QUESTION);
        }
        auditTrail.record(new AuditEntry(actor.organizationId(), actor.userId(), actor.email(),
                AuditAction.AI_COPILOT_QUERY, "COPILOT", null, AuditOutcome.SUCCESS, null, null,
                Map.of("length", String.valueOf(q.length())), clock.instant()));

        List<GenerativeAiPort.AiMessage> messages = new ArrayList<>();
        List<Turn> recent = history == null ? List.of() : history.subList(Math.max(0, history.size() - MAX_HISTORY), history.size());
        for (Turn turn : recent) {
            if (turn.content() == null || turn.content().isBlank()) {
                continue;
            }
            messages.add(new GenerativeAiPort.AiMessage("assistant".equalsIgnoreCase(turn.role())
                    ? GenerativeAiPort.Role.ASSISTANT : GenerativeAiPort.Role.USER, truncate(turn.content())));
        }
        messages.add(new GenerativeAiPort.AiMessage(GenerativeAiPort.Role.USER, "<data>Current UTC time: "
                + clock.instant() + "</data>\n\n" + q));

        final String finalQuestion = q;
        return ai.generate(actor.organizationId(), actor.userId(),
                        new GenerativeAiPort.Request("copilot", SYSTEM_PROMPT, mergeConsecutive(messages),
                                toolbox.definitions(), null, 4000),
                        (name, input) -> toolbox.execute(actor.organizationId(), name, input))
                .map(result -> new CopilotReply(result.text(), result.toolsUsed(), suggestions(finalQuestion),
                        result.engine(), clock.instant()))
                .orElseGet(() -> offline(actor.organizationId(), finalQuestion));
    }

    // ------------------------------------------------------- offline engine

    CopilotReply offline(long organizationId, String question) {
        String q = question.toLowerCase(Locale.ROOT);
        int hours = parseHours(q);
        List<String> tools = new ArrayList<>();
        StringBuilder answer = new StringBuilder();

        Matcher ref = PAYMENT_REF.matcher(question);
        if (ref.find()) {
            tools.add("get_payment");
            answer.append(paymentAnswer(organizationId, ref.group().toUpperCase(Locale.ROOT)));
        } else if (containsAny(q, "forecast", "predict", "expect", "next hour", "tomorrow", "upcoming")) {
            tools.add("forecast_volume");
            answer.append(forecastAnswer(organizationId));
        } else if (containsAny(q, "route", "routing", "best provider", "which provider", "switch", "cheapest")) {
            tools.add("recommend_routing");
            answer.append(routingAnswer(organizationId));
        } else if (containsAny(q, "fail", "decline", "error", "why", "reject")) {
            tools.add("get_failure_reasons");
            answer.append(failureAnswer(organizationId, hours));
        } else if (containsAny(q, "incident", "outage", "down", "issue")) {
            tools.add("get_active_incidents");
            answer.append(incidentAnswer(organizationId));
        } else if (containsAny(q, "provider", "acquirer", "gateway", "latency", "slow")) {
            tools.add("get_provider_performance");
            answer.append(providerAnswer(organizationId, hours));
        } else if (containsAny(q, "risk", "fraud", "suspicious", "aml")) {
            tools.add("search_payments");
            answer.append(riskAnswer(organizationId, hours));
        } else if (containsAny(q, "country", "corridor", "geograph", "region")) {
            tools.add("get_corridors");
            answer.append(corridorAnswer(organizationId, hours));
        } else {
            tools.add("get_kpis");
            tools.add("get_active_incidents");
            answer.append(overviewAnswer(organizationId, hours));
        }
        return new CopilotReply(answer.toString().trim(), tools, suggestions(question), ai.deterministicEngine(),
                clock.instant());
    }

    private String overviewAnswer(long org, int hours) {
        AnalyticsModels.Overview o = data.overview(org, Duration.ofHours(hours));
        AnalyticsModels.WindowStats s = o.current();
        List<IncidentView> incidents = data.activeIncidents(org);
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("**Last %dh:** %,d payments, **%.1f%%** success, **$%,.0f** processed, p95 latency **%d ms**.%n%n",
                hours, s.total(), s.successRate(), s.volumeUsd(), s.p95LatencyMs()));
        sb.append("| KPI | Now | Change |\n|---|---|---|\n");
        for (AnalyticsModels.Kpi kpi : o.kpis()) {
            Double change = kpi.changePercent();
            sb.append("| ").append(kpi.label()).append(" | ").append(formatKpi(kpi)).append(" | ")
                    .append(change == null ? "—" : String.format("%+.1f%%", change)).append(" |\n");
        }
        sb.append('\n');
        if (incidents.isEmpty()) {
            sb.append("No active incidents.");
        } else {
            sb.append("**Active incidents:**\n");
            incidents.stream().limit(5).forEach(i -> sb.append("- `").append(i.reference()).append("` ")
                    .append(i.severity()).append(" — ").append(i.title()).append('\n'));
        }
        return sb.toString();
    }

    private String failureAnswer(long org, int hours) {
        List<Map<String, Object>> reasons = toolbox.failureReasons(org, hours);
        if (reasons.isEmpty()) {
            return "No failed payments in the last " + hours + "h.";
        }
        StringBuilder sb = new StringBuilder("**Top failure reasons, last " + hours + "h:**\n\n| Code | Count | Category | Retryable |\n|---|---|---|---|\n");
        reasons.stream().limit(6).forEach(r -> sb.append("| ").append(r.get("code")).append(" | ").append(r.get("count"))
                .append(" | ").append(r.get("category")).append(" | ").append(Boolean.TRUE.equals(r.get("retryable")) ? "Yes" : "No")
                .append(" |\n"));
        FailureKnowledgeBase.FailureInsight top = FailureKnowledgeBase.lookup(String.valueOf(reasons.get(0).get("code")));
        sb.append("\n**Biggest driver — ").append(top.code()).append(":** ").append(top.meaning())
                .append("\n\n**Recommended action:** ").append(top.operatorAction());
        return sb.toString();
    }

    private String providerAnswer(long org, int hours) {
        List<AnalyticsModels.ProviderPerformance> perf = data.providerPerformance(org, Duration.ofHours(hours));
        StringBuilder sb = new StringBuilder("**Provider performance, last " + hours + "h:**\n\n| Provider | Volume (USD) | Success | p95 | SLA |\n|---|---|---|---|---|\n");
        perf.forEach(p -> sb.append(String.format("| %s | $%,.0f | %.1f%% | %d ms | %s%% / %d ms |%n", p.name(),
                p.volumeUsd(), p.successRate(), p.p95LatencyMs(), p.slaSuccessRate().stripTrailingZeros().toPlainString(),
                p.slaLatencyMs())));
        perf.stream().filter(p -> p.succeeded() + p.failed() >= 10 && p.successRate() < p.slaSuccessRate().doubleValue())
                .forEach(p -> sb.append(String.format("%n**%s** is below its success SLA (%.1f%% vs %s%%).", p.name(),
                        p.successRate(), p.slaSuccessRate().stripTrailingZeros().toPlainString())));
        return sb.toString();
    }

    private String routingAnswer(long org) {
        List<RoutingAdvisor.Ranking> ranking = toolbox.routing(org);
        if (ranking.isEmpty()) {
            return "No active providers to rank yet.";
        }
        StringBuilder sb = new StringBuilder("**Smart-routing ranking (last hour):**\n\n| # | Provider | Score | Why |\n|---|---|---|---|\n");
        for (int i = 0; i < ranking.size(); i++) {
            RoutingAdvisor.Ranking r = ranking.get(i);
            sb.append(String.format("| %d | %s | %.0f | %s |%n", i + 1, r.name(), r.score(), r.rationale()));
        }
        sb.append("\nRoute new traffic preferentially to **").append(ranking.get(0).name()).append("**.");
        return sb.toString();
    }

    private String forecastAnswer(long org) {
        List<VolumeForecaster.Point> points = toolbox.forecast(org, 6);
        if (points.isEmpty()) {
            return "Not enough history yet to forecast volume (needs at least 3 hours).";
        }
        StringBuilder sb = new StringBuilder("**Volume forecast (payments per hour, 80% band):**\n\n| Hour (UTC) | Expected | Range |\n|---|---|---|\n");
        points.forEach(p -> sb.append(String.format("| %s | %.0f | %.0f – %.0f |%n", HOUR_FORMAT.format(p.at()),
                p.expected(), p.lower(), p.upper())));
        return sb.toString();
    }

    private String incidentAnswer(long org) {
        List<IncidentView> incidents = data.activeIncidents(org);
        if (incidents.isEmpty()) {
            return "No active incidents. All providers are operating without correlated alerts.";
        }
        StringBuilder sb = new StringBuilder("**" + incidents.size() + " active incident(s):**\n\n");
        incidents.forEach(i -> {
            sb.append("- `").append(i.reference()).append("` **").append(i.severity()).append("** — ").append(i.title())
                    .append(String.format(" (%d payments, $%,.0f impacted, status %s)%n", i.affectedPayments(),
                            i.affectedAmount(), i.status()));
            if (i.aiAnalysis() != null) {
                sb.append("  - Likely cause: ").append(i.aiAnalysis().rootCause()).append('\n');
            }
        });
        sb.append("\nOpen an incident and press **Investigate with AI** for a full root-cause analysis.");
        return sb.toString();
    }

    private String riskAnswer(long org, int hours) {
        List<Map<String, Object>> risky = toolbox.searchPayments(org, Map.of("risk_level", "HIGH", "window_hours", hours));
        if (risky.isEmpty()) {
            return "No HIGH or CRITICAL risk payments in the last " + hours + "h.";
        }
        StringBuilder sb = new StringBuilder("**High-risk payments, last " + hours + "h:**\n\n| Reference | Amount | Risk | Status |\n|---|---|---|---|\n");
        risky.stream().limit(10).forEach(p -> sb.append("| `").append(p.get("reference")).append("` | ").append(p.get("amount"))
                .append(' ').append(p.get("currency")).append(" | ").append(p.get("riskScore")).append(" (").append(p.get("riskLevel"))
                .append(") | ").append(p.get("status")).append(" |\n"));
        sb.append("\nAsk me about a reference to see the factors behind its score.");
        return sb.toString();
    }

    private String corridorAnswer(long org, int hours) {
        List<AnalyticsModels.Corridor> corridors = data.corridors(org, Duration.ofHours(hours));
        StringBuilder sb = new StringBuilder("**Top corridors, last " + hours + "h:**\n\n| Country | Payments | Volume (USD) | Success |\n|---|---|---|---|\n");
        corridors.stream().limit(10).forEach(c -> sb.append(String.format("| %s | %,d | $%,.0f | %.1f%% |%n", c.country(),
                c.count(), c.volumeUsd(), c.successRate())));
        return sb.toString();
    }

    private String paymentAnswer(long org, String reference) {
        return data.payment(org, reference).map(d -> {
            var p = d.payment();
            StringBuilder sb = new StringBuilder(String.format("**%s** — %s %s via %s, status **%s**.%n%n", p.reference(),
                    p.amount(), p.currency(), p.providerName(), p.status()));
            if (p.failureCode() != null) {
                FailureKnowledgeBase.FailureInsight insight = FailureKnowledgeBase.lookup(p.failureCode());
                sb.append("**Failure:** ").append(p.failureCode()).append(" — ").append(insight.meaning()).append("\n\n")
                        .append("**Retry:** ").append(insight.retryable() && d.retryable() ? "Recommended. " : "Not recommended. ")
                        .append(insight.operatorAction()).append("\n\n");
            }
            sb.append(String.format("**Risk:** %d/100 (%s)", p.riskScore(), p.riskLevel()));
            if (!p.riskFactors().isEmpty()) {
                sb.append("\n");
                p.riskFactors().forEach(f -> sb.append("- ").append(f).append('\n'));
            }
            return sb.toString();
        }).orElse("I couldn't find payment `" + reference + "` in your organisation.");
    }

    // ------------------------------------------------------------- helpers

    private static List<String> suggestions(String question) {
        String q = question.toLowerCase(Locale.ROOT);
        List<String> all = List.of(
                "How are we performing in the last 24 hours?",
                "Why are payments failing right now?",
                "Which provider should we route to?",
                "Forecast volume for the next 6 hours",
                "Show high-risk payments from today",
                "Summarise active incidents");
        return all.stream().filter(s -> !s.toLowerCase(Locale.ROOT).equals(q)).limit(4).toList();
    }

    private static String formatKpi(AnalyticsModels.Kpi kpi) {
        return switch (kpi.unit()) {
            case "USD" -> String.format("$%,.0f", kpi.value());
            case "%" -> String.format("%.1f%%", kpi.value());
            case "ms" -> String.format("%.0f ms", kpi.value());
            default -> String.format("%,.0f", kpi.value());
        };
    }

    private static int parseHours(String q) {
        Matcher m = HOURS.matcher(q);
        if (m.find()) {
            return Math.max(1, Math.min(720, Integer.parseInt(m.group(1))));
        }
        if (q.contains("week")) {
            return 168;
        }
        if (q.contains("hour")) {
            return 1;
        }
        return 24;
    }

    private static boolean containsAny(String text, String... needles) {
        for (String n : needles) {
            if (text.contains(n)) {
                return true;
            }
        }
        return false;
    }

    private static String truncate(String text) {
        return text.length() <= MAX_QUESTION * 2 ? text : text.substring(0, MAX_QUESTION * 2);
    }

    /** The Messages API requires alternating roles; merge consecutive turns of the same role. */
    private static List<GenerativeAiPort.AiMessage> mergeConsecutive(List<GenerativeAiPort.AiMessage> messages) {
        List<GenerativeAiPort.AiMessage> merged = new ArrayList<>();
        for (GenerativeAiPort.AiMessage m : messages) {
            if (!merged.isEmpty() && merged.get(merged.size() - 1).role() == m.role()) {
                GenerativeAiPort.AiMessage last = merged.remove(merged.size() - 1);
                merged.add(new GenerativeAiPort.AiMessage(m.role(), last.content() + "\n\n" + m.content()));
            } else {
                merged.add(m);
            }
        }
        while (!merged.isEmpty() && merged.get(0).role() == GenerativeAiPort.Role.ASSISTANT) {
            merged.remove(0);
        }
        return merged;
    }
}
