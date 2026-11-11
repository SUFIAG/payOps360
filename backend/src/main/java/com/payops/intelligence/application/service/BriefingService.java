package com.payops.intelligence.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payops.alert.application.port.in.AlertQueryUseCase;
import com.payops.analytics.domain.AnalyticsModels;
import com.payops.incident.application.port.in.IncidentView;
import com.payops.intelligence.application.port.in.BriefingUseCase;
import com.payops.intelligence.application.port.in.InsightsUseCase;
import com.payops.intelligence.application.port.out.GenerativeAiPort;
import com.payops.intelligence.application.port.out.OperationsDataPort;
import com.payops.shared.application.Actor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BriefingService implements BriefingUseCase {

    private static final String PROMPT = """
            Write a concise executive operations briefing (markdown, max 300 words) for the head of payments,
            covering the last 24 hours: overall performance vs the previous day, provider health, top failure
            drivers, incidents, risk, and the 3 most valuable actions for today. Use the data in <data> tags only.
            Start with a one-line verdict in bold.
            """ + AiInvoker.GUARDRAILS;

    private final OperationsDataPort data;
    private final InsightsUseCase insights;
    private final AiInvoker ai;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Override
    public Briefing briefing(Actor actor) {
        long org = actor.organizationId();
        AnalyticsModels.Overview overview = data.overview(org, Duration.ofHours(24));
        List<AnalyticsModels.ProviderPerformance> providers = data.providerPerformance(org, Duration.ofHours(24));
        List<AnalyticsModels.Breakdown> failures = data.failureReasons(org, Duration.ofHours(24));
        List<IncidentView> incidents = data.activeIncidents(org);
        AlertQueryUseCase.Summary alerts = data.alertSummary(org);
        InsightsUseCase.OperationalInsights live = insights.insights(org);

        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("kpis", overview.kpis());
        facts.put("providers", providers);
        facts.put("failureReasons", failures);
        facts.put("activeIncidents", incidents);
        facts.put("alerts", alerts);
        facts.put("healthScore", live.healthScore());
        facts.put("recommendations", live.recommendations());

        return ai.generate(org, actor.userId(), new GenerativeAiPort.Request("daily-briefing", PROMPT,
                                List.of(new GenerativeAiPort.AiMessage(GenerativeAiPort.Role.USER,
                                        "<data>" + toJson(facts) + "</data>\n\nWrite today's briefing.")),
                                List.of(), null, 3000),
                        GenerativeAiPort.ToolExecutor.none())
                .map(r -> new Briefing(r.text(), r.engine(), clock.instant()))
                .orElseGet(() -> new Briefing(deterministic(overview, providers, failures, incidents, live),
                        ai.deterministicEngine(), clock.instant()));
    }

    private static String deterministic(AnalyticsModels.Overview overview, List<AnalyticsModels.ProviderPerformance> providers,
                                        List<AnalyticsModels.Breakdown> failures, List<IncidentView> incidents,
                                        InsightsUseCase.OperationalInsights live) {
        AnalyticsModels.WindowStats now = overview.current();
        AnalyticsModels.WindowStats before = overview.previous();
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("**Operations health %d/100 (%s).** %s%n%n", live.healthScore(),
                live.healthStatus().toLowerCase(), live.headline()));
        sb.append("### Last 24 hours\n");
        sb.append(String.format("- **$%,.0f** processed across **%,d** payments (%s vs previous day)%n", now.volumeUsd(),
                now.total(), change(now.volumeUsd().doubleValue(), before.volumeUsd().doubleValue())));
        sb.append(String.format("- Success rate **%.2f%%** (%s), p95 latency **%d ms**%n", now.successRate(),
                before.total() == 0 ? "no baseline" : String.format("%+.2f pts", now.successRate() - before.successRate()),
                now.p95LatencyMs()));
        sb.append(String.format("- Revenue at risk from failures: **$%,.0f**; high-risk payments: **%d**%n%n",
                now.failedVolumeUsd(), now.highRisk()));
        sb.append("### Providers\n");
        providers.stream().filter(p -> p.total() > 0).forEach(p -> sb.append(String.format("- %s: %.1f%% success, p95 %d ms, $%,.0f%s%n",
                p.name(), p.successRate(), p.p95LatencyMs(), p.volumeUsd(),
                p.successRate() < p.slaSuccessRate().doubleValue() ? " — **below SLA**" : "")));
        if (!failures.isEmpty()) {
            sb.append("\n### Top failure drivers\n");
            failures.stream().limit(3).forEach(f -> sb.append(String.format("- %s: %,d payments%n", f.label(), f.count())));
        }
        sb.append("\n### Incidents\n");
        if (incidents.isEmpty()) {
            sb.append("- None active\n");
        } else {
            incidents.forEach(i -> sb.append(String.format("- %s %s — %s%n", i.reference(), i.severity(), i.title())));
        }
        if (!live.recommendations().isEmpty()) {
            sb.append("\n### Recommended actions\n");
            live.recommendations().stream().limit(3).forEach(r -> sb.append("1. **").append(r.title()).append("** — ")
                    .append(r.action()).append('\n'));
        }
        return sb.toString();
    }

    private static String change(double now, double before) {
        if (before == 0) {
            return "no baseline";
        }
        return String.format("%+.1f%%", (now - before) * 100 / before);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
