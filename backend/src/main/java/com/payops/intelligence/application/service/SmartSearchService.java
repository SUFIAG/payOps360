package com.payops.intelligence.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payops.intelligence.application.port.input.SmartSearchUseCase;
import com.payops.intelligence.application.port.output.GenerativeAiPort;
import com.payops.intelligence.application.port.output.OperationsDataPort;
import com.payops.payment.domain.PaymentMethod;
import com.payops.payment.domain.PaymentStatus;
import com.payops.provider.application.port.input.ProviderView;
import com.payops.shared.application.Actor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Currency;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Natural-language payment search. A deterministic parser handles the common
 * vocabulary; when an LLM is enabled it interprets free-form phrasing and its
 * output is validated against the allowed values before use.
 */
@Service
@RequiredArgsConstructor
public class SmartSearchService implements SmartSearchUseCase {

    private static final Pattern OVER = Pattern.compile("(?:over|above|more than|greater than|>|at least|min(?:imum)?)\\s*\\$?\\s*([\\d,]+(?:\\.\\d+)?)\\s*(k)?");
    private static final Pattern UNDER = Pattern.compile("(?:under|below|less than|<|at most|max(?:imum)?)\\s*\\$?\\s*([\\d,]+(?:\\.\\d+)?)\\s*(k)?");
    private static final Pattern LAST_HOURS = Pattern.compile("(?:last|past)\\s+(\\d{1,3})\\s*(h|hr|hrs|hour|hours|d|day|days)");
    private static final Pattern REFERENCE = Pattern.compile("\\b(PAY-[A-Z0-9]{4,12})\\b", Pattern.CASE_INSENSITIVE);
    private static final Map<String, String> COUNTRY_NAMES = Map.ofEntries(
            Map.entry("pakistan", "PK"), Map.entry("india", "IN"), Map.entry("philippines", "PH"),
            Map.entry("bangladesh", "BD"), Map.entry("egypt", "EG"), Map.entry("nigeria", "NG"),
            Map.entry("mexico", "MX"), Map.entry("kenya", "KE"), Map.entry("saudi", "SA"), Map.entry("uae", "AE"),
            Map.entry("emirates", "AE"), Map.entry("uk", "GB"), Map.entry("united kingdom", "GB"),
            Map.entry("britain", "GB"), Map.entry("usa", "US"), Map.entry("united states", "US"),
            Map.entry("germany", "DE"), Map.entry("spain", "ES"), Map.entry("france", "FR"));

    private static final String PROMPT = """
            Convert a payment-search request into filters. Only use these values:
            statuses: %s
            methods: %s
            risk: LOW, MEDIUM, HIGH, CRITICAL (minimum level)
            providers (codes): %s
            Use null for anything not mentioned. lastHours is the look-back window in hours.
            Write a one-sentence explanation of how you interpreted the request.
            """ + AiInvoker.GUARDRAILS;

    private final OperationsDataPort data;
    private final AiInvoker ai;
    private final ObjectMapper objectMapper;

    @Override
    public SearchInterpretation interpret(Actor actor, String query) {
        String q = query == null ? "" : query.strip();
        if (q.length() > 500) {
            q = q.substring(0, 500);
        }
        List<ProviderView> providers = data.providers(actor.organizationId());
        SearchInterpretation parsed = parse(q, providers);
        if (!ai.enabledFor(actor.organizationId()) || q.isBlank()) {
            return parsed;
        }
        Set<String> providerCodes = providers.stream().map(ProviderView::code).collect(Collectors.toSet());
        String system = PROMPT.formatted(Arrays.toString(PaymentStatus.values()), Arrays.toString(PaymentMethod.values()), providerCodes);
        final String userQuery = q;
        return ai.generate(actor.organizationId(), actor.userId(),
                        new GenerativeAiPort.Request("smart-search", system,
                                List.of(new GenerativeAiPort.AiMessage(GenerativeAiPort.Role.USER, "<data>" + userQuery + "</data>")),
                                List.of(), schema(), 1000),
                        GenerativeAiPort.ToolExecutor.none())
                .flatMap(result -> fromLlm(result.text(), result.engine(), providerCodes))
                .orElse(parsed);
    }

    SearchInterpretation parse(String query, List<ProviderView> providers) {
        String q = query.toLowerCase(Locale.ROOT);
        List<String> notes = new ArrayList<>();

        Set<String> statuses = new LinkedHashSet<>();
        if (q.matches(".*\\b(fail|failed|failing|failure|declin\\w*)\\b.*")) {
            statuses.addAll(List.of("FAILED", "RETRY_FAILED"));
        }
        if (q.matches(".*\\b(success\\w*|settled|completed|paid)\\b.*")) {
            statuses.add("SETTLED");
        }
        if (q.matches(".*\\b(pending|in[- ]flight|processing)\\b.*")) {
            statuses.addAll(List.of("INITIATED", "AUTHORIZED", "CAPTURED", "PROCESSING"));
        }
        if (q.contains("refund")) {
            statuses.add("REFUNDED");
        }
        if (q.contains("chargeback")) {
            statuses.add("CHARGEBACK");
        }
        if (q.contains("cancel")) {
            statuses.add("CANCELLED");
        }
        if (!statuses.isEmpty()) {
            notes.add("status " + String.join("/", statuses).toLowerCase(Locale.ROOT));
        }

        String provider = providers.stream()
                .filter(p -> q.contains(p.code().toLowerCase(Locale.ROOT)) || q.contains(p.name().toLowerCase(Locale.ROOT)))
                .map(ProviderView::code).findFirst().orElse(null);
        if (provider != null) {
            notes.add("provider " + provider);
        }

        String method = null;
        if (q.contains("card")) {
            method = "CARD";
        } else if (q.contains("wallet")) {
            method = "DIGITAL_WALLET";
        } else if (q.contains("bank") || q.contains("transfer") || q.contains("wire")) {
            method = "BANK_TRANSFER";
        } else if (q.contains("cash")) {
            method = "CASH_PICKUP";
        } else if (q.contains("instant") || q.contains("real-time") || q.contains("rtp")) {
            method = "REAL_TIME_PAYMENT";
        } else if (q.contains("bnpl") || q.contains("buy now")) {
            method = "BNPL";
        }
        if (method != null) {
            notes.add("method " + method.toLowerCase(Locale.ROOT).replace('_', ' '));
        }

        String risk = null;
        if (q.contains("critical")) {
            risk = "CRITICAL";
        } else if (q.matches(".*\\b(high[- ]risk|risky|suspicious|fraud\\w*)\\b.*")) {
            risk = "HIGH";
        } else if (q.contains("medium risk")) {
            risk = "MEDIUM";
        }
        if (risk != null) {
            notes.add("risk ≥ " + risk.toLowerCase(Locale.ROOT));
        }

        String currency = null;
        for (String token : query.split("[^A-Za-z]+")) {
            if (token.length() == 3 && token.equals(token.toUpperCase(Locale.ROOT)) && isCurrency(token)) {
                currency = token;
                break;
            }
        }
        if (currency == null && q.contains("$")) {
            currency = "USD";
        }
        if (currency != null) {
            notes.add("currency " + currency);
        }

        BigDecimal minAmount = amount(OVER.matcher(q));
        BigDecimal maxAmount = amount(UNDER.matcher(q));
        if (minAmount != null) {
            notes.add("amount ≥ " + minAmount.toPlainString());
        }
        if (maxAmount != null) {
            notes.add("amount ≤ " + maxAmount.toPlainString());
        }

        String country = COUNTRY_NAMES.entrySet().stream().filter(e -> q.matches(".*\\b" + e.getKey() + "\\b.*"))
                .map(Map.Entry::getValue).findFirst().orElse(null);
        if (country != null) {
            notes.add("destination " + country);
        }

        Integer hours = null;
        Matcher last = LAST_HOURS.matcher(q);
        if (last.find()) {
            int n = Integer.parseInt(last.group(1));
            hours = last.group(2).startsWith("d") ? n * 24 : n;
        } else if (q.contains("today")) {
            hours = 24;
        } else if (q.contains("yesterday")) {
            hours = 48;
        } else if (q.contains("this week") || q.contains("last week")) {
            hours = 168;
        } else if (q.contains("last hour") || q.contains("past hour")) {
            hours = 1;
        }
        if (hours != null) {
            notes.add("last " + hours + "h");
        }

        Boolean stuck = q.contains("stuck") ? Boolean.TRUE : null;
        if (stuck != null) {
            notes.add("stuck only");
        }

        Matcher ref = REFERENCE.matcher(query);
        String text = ref.find() ? ref.group(1).toUpperCase(Locale.ROOT) : null;

        String explanation = notes.isEmpty()
                ? (text == null ? "Showing all payments — no filters recognised." : "Looking up " + text + ".")
                : "Showing payments with " + String.join(", ", notes) + ".";
        return new SearchInterpretation(new ArrayList<>(statuses), provider, method, risk, currency, minAmount, maxAmount,
                country, hours, stuck, text, explanation, ai.deterministicEngine());
    }

    private Optional<SearchInterpretation> fromLlm(String json, String engine, Set<String> providerCodes) {
        try {
            JsonNode n = objectMapper.readTree(json);
            List<String> statuses = new ArrayList<>();
            n.path("statuses").forEach(s -> {
                String v = s.asText().toUpperCase(Locale.ROOT);
                if (Arrays.stream(PaymentStatus.values()).anyMatch(ps -> ps.name().equals(v))) {
                    statuses.add(v);
                }
            });
            String provider = text(n, "provider").filter(providerCodes::contains).orElse(null);
            String method = text(n, "method").map(v -> v.toUpperCase(Locale.ROOT))
                    .filter(v -> Arrays.stream(PaymentMethod.values()).anyMatch(m -> m.name().equals(v))).orElse(null);
            String risk = text(n, "risk").map(v -> v.toUpperCase(Locale.ROOT))
                    .filter(v -> Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL").contains(v)).orElse(null);
            String currency = text(n, "currency").map(v -> v.toUpperCase(Locale.ROOT)).filter(SmartSearchService::isCurrency).orElse(null);
            String country = text(n, "country").map(v -> v.toUpperCase(Locale.ROOT)).filter(v -> v.matches("[A-Z]{2}")).orElse(null);
            BigDecimal min = n.path("minAmount").isNumber() ? n.path("minAmount").decimalValue() : null;
            BigDecimal max = n.path("maxAmount").isNumber() ? n.path("maxAmount").decimalValue() : null;
            Integer hours = n.path("lastHours").isNumber() ? Math.max(1, Math.min(2160, n.path("lastHours").asInt())) : null;
            Boolean stuck = n.path("stuck").isBoolean() ? n.path("stuck").asBoolean() : null;
            String explanation = text(n, "explanation").orElse("Interpreted by AI.");
            return Optional.of(new SearchInterpretation(statuses, provider, method, risk, currency, min, max, country,
                    hours, stuck, null, explanation, engine));
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }

    private static Map<String, Object> schema() {
        Map<String, Object> nullableString = Map.of("type", List.of("string", "null"));
        Map<String, Object> nullableNumber = Map.of("type", List.of("number", "null"));
        return Map.of(
                "type", "object",
                "properties", Map.ofEntries(
                        Map.entry("statuses", Map.of("type", "array", "items", Map.of("type", "string"))),
                        Map.entry("provider", nullableString),
                        Map.entry("method", nullableString),
                        Map.entry("risk", nullableString),
                        Map.entry("currency", nullableString),
                        Map.entry("country", nullableString),
                        Map.entry("minAmount", nullableNumber),
                        Map.entry("maxAmount", nullableNumber),
                        Map.entry("lastHours", Map.of("type", List.of("integer", "null"))),
                        Map.entry("stuck", Map.of("type", List.of("boolean", "null"))),
                        Map.entry("explanation", Map.of("type", "string"))),
                "required", List.of("statuses", "provider", "method", "risk", "currency", "country", "minAmount",
                        "maxAmount", "lastHours", "stuck", "explanation"),
                "additionalProperties", false);
    }

    private static Optional<String> text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isTextual() && !v.asText().isBlank() ? Optional.of(v.asText()) : Optional.empty();
    }

    private static BigDecimal amount(Matcher matcher) {
        if (!matcher.find()) {
            return null;
        }
        BigDecimal value = new BigDecimal(matcher.group(1).replace(",", ""));
        return matcher.group(2) != null ? value.multiply(BigDecimal.valueOf(1000)) : value;
    }

    private static boolean isCurrency(String code) {
        try {
            Currency.getInstance(code);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
