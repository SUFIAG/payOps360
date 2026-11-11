package com.payops.intelligence.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payops.audit.application.port.in.AuditTrail;
import com.payops.audit.domain.AuditAction;
import com.payops.audit.domain.AuditEntry;
import com.payops.audit.domain.AuditOutcome;
import com.payops.incident.application.port.in.IncidentQueryUseCase;
import com.payops.incident.application.port.in.IncidentView;
import com.payops.incident.application.port.in.PostmortemUseCase;
import com.payops.incident.domain.AiAnalysis;
import com.payops.incident.domain.IncidentStatus;
import com.payops.incident.domain.PostmortemDraft;
import com.payops.intelligence.application.port.in.PostmortemDraftUseCase;
import com.payops.intelligence.application.port.out.GenerativeAiPort;
import com.payops.intelligence.application.port.out.OperationsDataPort;
import com.payops.shared.application.Actor;
import com.payops.shared.application.RequestContext;
import com.payops.shared.domain.DomainException;
import com.payops.shared.domain.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.RoundingMode;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Drafts a blameless postmortem from the incident record, its timeline and
 * any AI root-cause analysis. Like the investigation, evidence is gathered
 * deterministically; the LLM (when enabled) only writes the narrative, and the
 * built-in engine produces the same structure without it. The draft is
 * advisory: it lands in the DRAFT postmortem for a human to edit and publish.
 */
@Service
@RequiredArgsConstructor
public class PostmortemDraftService implements PostmortemDraftUseCase {

    private static final String PROMPT = """
            You are a principal payments site-reliability engineer writing a blameless postmortem for PayOps360.
            You receive the incident record, its timeline and any prior analysis inside <data> tags. Produce:
            - summary: 3-4 sentences on what happened and how it was resolved, for an executive audience.
            - impact: customer and business impact (payments, amount at risk, duration, SLA breaches).
            - rootCause: the most likely root cause and contributing factors, citing the timeline. Blameless tone.
            - actionItems: 3-6 concrete, verifiable follow-ups that prevent recurrence or speed up detection;
              owner is a team or role (e.g. "Payments SRE"), never a person's name.
            """ + AiInvoker.GUARDRAILS;

    private static final Map<String, Object> SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of(
                    "summary", Map.of("type", "string"),
                    "impact", Map.of("type", "string"),
                    "rootCause", Map.of("type", "string"),
                    "actionItems", Map.of("type", "array", "items", Map.of(
                            "type", "object",
                            "properties", Map.of("text", Map.of("type", "string"), "owner", Map.of("type", "string")),
                            "required", List.of("text", "owner"),
                            "additionalProperties", false))),
            "required", List.of("summary", "impact", "rootCause", "actionItems"),
            "additionalProperties", false);

    private final OperationsDataPort data;
    private final PostmortemUseCase postmortems;
    private final AiInvoker ai;
    private final AuditTrail auditTrail;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Override
    public PostmortemUseCase.PostmortemView draftPostmortem(Actor actor, String incidentReference,
                                                            RequestContext context) {
        IncidentQueryUseCase.IncidentDetails details = data.incident(actor.organizationId(), incidentReference)
                .orElseThrow(() -> new NotFoundException("Incident", incidentReference));
        if (details.incident().status() != IncidentStatus.RESOLVED) {
            throw new DomainException("INCIDENT_NOT_RESOLVED", "Postmortems can only be written for resolved incidents");
        }
        PostmortemDraft deterministic = deterministicDraft(details);
        PostmortemDraft draft = ai.generate(actor.organizationId(), actor.userId(),
                        new GenerativeAiPort.Request("incident-postmortem", PROMPT,
                                List.of(new GenerativeAiPort.AiMessage(GenerativeAiPort.Role.USER,
                                        "<data>" + toJson(evidence(details)) + "</data>\n\nDraft the postmortem.")),
                                List.of(), SCHEMA, 4000),
                        GenerativeAiPort.ToolExecutor.none())
                .flatMap(result -> parse(result.text(), result.engine()))
                .orElse(deterministic);

        auditTrail.record(new AuditEntry(actor.organizationId(), actor.userId(), actor.email(),
                AuditAction.AI_POSTMORTEM_DRAFT, "INCIDENT", incidentReference, AuditOutcome.SUCCESS,
                context.ipAddress(), context.userAgent(), Map.of("engine", draft.engine()), clock.instant()));
        return postmortems.applyDraft(actor, incidentReference, draft);
    }

    PostmortemDraft deterministicDraft(IncidentQueryUseCase.IncidentDetails details) {
        IncidentView i = details.incident();
        AiAnalysis analysis = i.aiAnalysis();
        String duration = i.minutesToResolve() == null ? "an unknown duration" : i.minutesToResolve() + " minutes";
        String summary = String.format("%s incident \"%s\"%s was open for %s before it was resolved by %s. %s",
                i.severity(), i.title(), i.providerCode() == null ? "" : " on provider " + i.providerCode(), duration,
                i.resolvedBy() == null ? "the operations team" : i.resolvedBy(),
                i.resolutionNote() == null ? "" : "Resolution: " + i.resolutionNote());

        StringBuilder impact = new StringBuilder(String.format("%d payment(s) failed or were stuck, with about %s at risk.",
                i.affectedPayments(), i.affectedAmount().setScale(0, RoundingMode.HALF_UP).toPlainString()));
        if (i.minutesToAcknowledge() != null) {
            impact.append(" Time to acknowledge: ").append(i.minutesToAcknowledge()).append(" min.");
        }
        if (i.minutesToResolve() != null) {
            impact.append(" Time to resolve: ").append(i.minutesToResolve()).append(" min.");
        }
        if (i.sla() != null && (i.sla().ackBreached() || i.sla().resolveBreached())) {
            impact.append(" The ").append(i.sla().ackBreached() && i.sla().resolveBreached() ? "acknowledge and resolve"
                    : i.sla().ackBreached() ? "acknowledge" : "resolve").append(" SLA was breached.");
        }

        long alerts = details.timeline().stream().filter(t -> t.kind().name().equals("ALERT_CORRELATED")).count();
        String rootCause = analysis != null ? analysis.rootCause()
                : "Root cause not yet established. " + (alerts > 0 ? alerts + " correlated alert(s) and " : "")
                + "the resolution note (\"" + i.resolutionNote() + "\") are the starting point for the review.";

        List<PostmortemDraft.Item> items = new ArrayList<>();
        if (analysis != null) {
            analysis.recommendations().stream().limit(4).forEach(r -> items.add(new PostmortemDraft.Item(r, "Operations")));
        }
        if (i.sla() != null && i.sla().ackBreached()) {
            items.add(new PostmortemDraft.Item("Review on-call routing and the escalation policy so "
                    + i.severity() + " incidents are acknowledged within the SLA.", "Operations management"));
        }
        if (i.providerCode() != null) {
            items.add(new PostmortemDraft.Item("Agree a follow-up with " + i.providerCode()
                    + " on the provider-side cause and their prevention plan.", "Partner management"));
        }
        items.add(new PostmortemDraft.Item("Add or tune the alert that would have detected this earlier.", "Payments SRE"));
        return new PostmortemDraft(summary.trim(), impact.toString(), rootCause, items, ai.deterministicEngine());
    }

    private Map<String, Object> evidence(IncidentQueryUseCase.IncidentDetails details) {
        IncidentView i = details.incident();
        Map<String, Object> map = new LinkedHashMap<>();
        Map<String, Object> incident = new LinkedHashMap<>();
        incident.put("reference", i.reference());
        incident.put("title", i.title());
        incident.put("description", String.valueOf(i.description()));
        incident.put("severity", i.severity());
        incident.put("provider", String.valueOf(i.providerCode()));
        incident.put("openedAt", i.openedAt());
        incident.put("acknowledgedAt", String.valueOf(i.acknowledgedAt()));
        incident.put("resolvedAt", String.valueOf(i.resolvedAt()));
        incident.put("minutesToAcknowledge", i.minutesToAcknowledge());
        incident.put("minutesToResolve", i.minutesToResolve());
        incident.put("affectedPayments", i.affectedPayments());
        incident.put("affectedAmount", i.affectedAmount());
        incident.put("resolutionNote", String.valueOf(i.resolutionNote()));
        incident.put("sla", i.sla());
        map.put("incident", incident);
        if (i.aiAnalysis() != null) {
            map.put("priorAnalysis", Map.of("summary", i.aiAnalysis().summary(), "rootCause", i.aiAnalysis().rootCause(),
                    "recommendations", i.aiAnalysis().recommendations()));
        }
        map.put("timeline", details.timeline().stream()
                .map(t -> t.occurredAt() + " " + t.kind() + ": " + t.message()).toList());
        return map;
    }

    private Optional<PostmortemDraft> parse(String json, String engine) {
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node.path("summary").asText().isBlank() || node.path("rootCause").asText().isBlank()) {
                return Optional.empty();
            }
            List<PostmortemDraft.Item> items = new ArrayList<>();
            node.path("actionItems").forEach(item -> {
                String text = item.path("text").asText();
                if (!text.isBlank()) {
                    items.add(new PostmortemDraft.Item(text, item.path("owner").asText(null)));
                }
            });
            return Optional.of(new PostmortemDraft(node.path("summary").asText(), node.path("impact").asText(),
                    node.path("rootCause").asText(), items.stream().limit(10).toList(), engine));
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
