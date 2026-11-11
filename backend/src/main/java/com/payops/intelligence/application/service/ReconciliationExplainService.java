package com.payops.intelligence.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payops.audit.application.port.in.AuditTrail;
import com.payops.audit.domain.AuditAction;
import com.payops.audit.domain.AuditEntry;
import com.payops.audit.domain.AuditOutcome;
import com.payops.intelligence.application.port.in.ReconciliationExplainUseCase;
import com.payops.intelligence.application.port.out.GenerativeAiPort;
import com.payops.reconciliation.application.port.in.ExceptionView;
import com.payops.reconciliation.application.port.in.ReconciliationQueryUseCase;
import com.payops.shared.application.Actor;
import com.payops.shared.application.RequestContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Explains a reconciliation exception. Evidence (exception, settlement line,
 * ledger payment) is gathered deterministically; the LLM, when enabled, only
 * reasons over it. The built-in engine applies per-type heuristics so the
 * feature works offline.
 */
@Service
@RequiredArgsConstructor
public class ReconciliationExplainService implements ReconciliationExplainUseCase {

    private static final String PROMPT = """
            You are a senior payments reconciliation analyst for PayOps360. You receive one reconciliation exception,
            the provider settlement line and the ledger payment it concerns inside <data> tags. Produce:
            - likelyCause: the most probable root cause in 2-3 sentences, citing the figures (amounts, dates, references).
            - suggestedAction: the single next action an operations analyst should take.
            - checks: 2-4 short verification steps that would confirm or rule out the cause.
            - confidence: LOW, MEDIUM or HIGH.
            """ + AiInvoker.GUARDRAILS;

    private static final Map<String, Object> SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of(
                    "likelyCause", Map.of("type", "string"),
                    "suggestedAction", Map.of("type", "string"),
                    "checks", Map.of("type", "array", "items", Map.of("type", "string")),
                    "confidence", Map.of("type", "string", "enum", List.of("LOW", "MEDIUM", "HIGH"))),
            "required", List.of("likelyCause", "suggestedAction", "checks", "confidence"),
            "additionalProperties", false);

    private final ReconciliationQueryUseCase reconciliation;
    private final AiInvoker ai;
    private final AuditTrail auditTrail;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Override
    public ExceptionExplanation explainException(Actor actor, String exceptionReference, RequestContext context) {
        ReconciliationQueryUseCase.ExceptionDetails details = reconciliation.exception(actor.organizationId(),
                exceptionReference);
        ExceptionExplanation explanation = ai.generate(actor.organizationId(), actor.userId(),
                        new GenerativeAiPort.Request("reconciliation-explain", PROMPT,
                                List.of(new GenerativeAiPort.AiMessage(GenerativeAiPort.Role.USER,
                                        "<data>" + toJson(evidence(details)) + "</data>\n\nExplain this exception.")),
                                List.of(), SCHEMA, 1500),
                        GenerativeAiPort.ToolExecutor.none())
                .flatMap(result -> parse(exceptionReference, result.text(), result.engine()))
                .orElseGet(() -> deterministic(details));
        auditTrail.record(new AuditEntry(actor.organizationId(), actor.userId(), actor.email(),
                AuditAction.AI_RECON_EXPLAIN, "RECON_EXCEPTION", exceptionReference, AuditOutcome.SUCCESS,
                context.ipAddress(), context.userAgent(), Map.of("engine", explanation.engine()), clock.instant()));
        return explanation;
    }

    ExceptionExplanation deterministic(ReconciliationQueryUseCase.ExceptionDetails details) {
        ExceptionView e = details.exception();
        ReconciliationQueryUseCase.LineView line = details.line();
        ReconciliationQueryUseCase.PaymentSnapshot payment = details.payment();
        String atRisk = e.amountAtRisk().toPlainString() + " " + e.currency();
        String cause;
        String action;
        List<String> checks = new ArrayList<>();
        String confidence = "MEDIUM";
        switch (e.type()) {
            case AMOUNT_MISMATCH -> {
                BigDecimal diff = e.amountAtRisk();
                boolean currencyGap = payment != null && line != null && !payment.currency().equals(line.currency());
                if (currencyGap) {
                    cause = "The provider settled in " + line.currency() + " while the payment was taken in "
                            + payment.currency() + " and no usable FX rate was reported, so the amounts cannot be compared.";
                    action = "Obtain the provider's FX rate for the settlement date and re-check the converted amount.";
                } else if (payment != null && diff.compareTo(payment.amount().multiply(new BigDecimal("0.05"))) <= 0) {
                    cause = "The settled amount differs from the ledger by " + atRisk + ", a small gap typical of "
                            + "fees netted from the gross amount, rounding, or a partial capture.";
                    action = "Compare the provider's gross vs net reporting for this line; if fees were netted, "
                            + "resolve as PROVIDER_CORRECTED or adjust the mapping to use the gross column.";
                    confidence = "HIGH";
                } else {
                    cause = "The settled amount differs from the ledger by " + atRisk + ". A large gap usually means a "
                            + "partial capture, an amended amount after authorisation, or a provider-side error.";
                    action = "Raise a query with the provider quoting reference " + e.providerReference()
                            + " and the expected amount " + e.expectedAmount() + ".";
                }
                checks.add("Check the payment's event history for amount changes or partial captures.");
                checks.add("Confirm whether the provider reports gross or net-of-fee amounts.");
            }
            case MISSING_IN_SETTLEMENT -> {
                long days = payment == null ? e.ageHours() / 24
                        : Duration.between(payment.createdAt(), clock.instant()).toDays();
                cause = "The payment succeeded " + days + " day(s) ago but no settlement file has reported it. "
                        + (days <= 3 ? "This is often a settlement-cycle timing difference (T+2/T+3 schedules or weekends)."
                        : "After this long it is likely held by the provider (risk review, reserve) or was never captured.");
                action = days <= 3 ? "Wait for the next settlement cycle; resolve as SETTLED_LATER once it appears."
                        : "Escalate to the provider with the payment reference and capture date to locate the funds.";
                confidence = days <= 3 ? "MEDIUM" : "HIGH";
                checks.add("Verify the payment was actually captured, not only authorised.");
                checks.add("Check whether the provider applied a rolling reserve or a compliance hold.");
            }
            case MISSING_IN_LEDGER -> {
                cause = "The provider settled " + atRisk + " under reference " + e.providerReference()
                        + " but no payment in the ledger carries that reference or matches its amount and date. "
                        + "Common causes: a payment created outside PayOps360, a reference format difference, "
                        + "or a payment that belongs to another merchant account.";
                action = "Search the ledger by amount and date; if the payment exists under another reference, "
                        + "use Manual match. Otherwise query the provider.";
                checks.add("Search payments for the same amount and currency around the settlement date.");
                checks.add("Check whether the reference uses a provider-side id instead of the PayOps reference.");
            }
            case DUPLICATE -> {
                cause = "Payment " + e.paymentReference() + " was already settled by another line; this second line "
                        + "settles it again, so " + atRisk + " may have been paid out twice.";
                action = "Confirm with the provider whether the duplicate was reversed in a later file; if not, "
                        + "request a correction and keep the exception open until it is netted off.";
                confidence = "HIGH";
                checks.add("Look for an offsetting negative line in later settlement files.");
                checks.add("Check whether the same file was split and re-sent by the provider.");
            }
            case FEE_OVERCHARGE -> {
                cause = "The provider charged " + e.actualAmount() + " " + e.currency() + " against an expected "
                        + e.expectedAmount() + " from the fee schedule, an overcharge of " + atRisk + ". This "
                        + "typically comes from a different card tier (premium/commercial/cross-border) or an "
                        + "outdated rate on the provider side.";
                action = "Verify the contracted rate for this method and currency; if the schedule is right, "
                        + "claim the difference from the provider, otherwise update the fee schedule.";
                checks.add("Check whether the card was premium, commercial or issued abroad.");
                checks.add("Compare against the latest pricing addendum with the provider.");
            }
            default -> {
                cause = "Unclassified exception.";
                action = "Review the settlement line and the payment manually.";
            }
        }
        return new ExceptionExplanation(e.reference(), cause, action, checks, confidence, ai.deterministicEngine(),
                clock.instant());
    }

    private Map<String, Object> evidence(ReconciliationQueryUseCase.ExceptionDetails details) {
        Map<String, Object> map = new LinkedHashMap<>();
        ExceptionView e = details.exception();
        Map<String, Object> exception = new LinkedHashMap<>();
        exception.put("type", e.type());
        exception.put("status", e.status());
        exception.put("provider", e.providerCode());
        exception.put("amountAtRisk", e.amountAtRisk() + " " + e.currency());
        exception.put("expectedAmount", String.valueOf(e.expectedAmount()));
        exception.put("actualAmount", String.valueOf(e.actualAmount()));
        exception.put("detail", String.valueOf(e.detail()));
        exception.put("ageHours", e.ageHours());
        map.put("exception", exception);
        if (details.line() != null) {
            map.put("settlementLine", details.line());
        }
        if (details.payment() != null) {
            map.put("ledgerPayment", details.payment());
        }
        return map;
    }

    private Optional<ExceptionExplanation> parse(String reference, String json, String engine) {
        try {
            JsonNode node = objectMapper.readTree(json);
            String cause = node.path("likelyCause").asText();
            String action = node.path("suggestedAction").asText();
            if (cause.isBlank() || action.isBlank()) {
                return Optional.empty();
            }
            List<String> checks = new ArrayList<>();
            node.path("checks").forEach(c -> {
                if (!c.asText().isBlank() && checks.size() < 6) {
                    checks.add(c.asText());
                }
            });
            String confidence = node.path("confidence").asText("MEDIUM");
            return Optional.of(new ExceptionExplanation(reference, cause, action, checks, confidence, engine,
                    clock.instant()));
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
