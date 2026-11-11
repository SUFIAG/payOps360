package com.payops.reconciliation.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Expected provider fees: {@code |amount| * percentage / 100 + fixedFee}. A
 * rule may be narrowed to a payment method and/or currency; the most
 * specific matching rule wins. When no rule applies, the provider's catalogue
 * cost (basis points) is used as a percentage-only fallback.
 */
public record FeeSchedule(List<Rule> rules, BigDecimal fallbackPercentage) {

    public record Rule(String method, String currency, BigDecimal percentage, BigDecimal fixedFee) {

        public Rule {
            if (percentage == null || percentage.signum() < 0 || percentage.compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new IllegalArgumentException("percentage must be between 0 and 100");
            }
            fixedFee = fixedFee == null ? BigDecimal.ZERO : fixedFee;
            if (fixedFee.signum() < 0) {
                throw new IllegalArgumentException("fixed fee must not be negative");
            }
            method = method == null || method.isBlank() ? null : method;
            currency = currency == null || currency.isBlank() ? null : currency.toUpperCase(Locale.ROOT);
        }

        boolean appliesTo(String paymentMethod, String paymentCurrency) {
            return (method == null || method.equals(paymentMethod)) && (currency == null || currency.equals(paymentCurrency));
        }

        int specificity() {
            return (method == null ? 0 : 2) + (currency == null ? 0 : 1);
        }

        String key() {
            return method + "|" + currency;
        }
    }

    public FeeSchedule {
        rules = rules == null ? List.of() : List.copyOf(rules);
        if (rules.stream().map(Rule::key).distinct().count() != rules.size()) {
            throw new IllegalArgumentException("each method/currency combination may appear only once");
        }
    }

    public Optional<Rule> ruleFor(String method, String currency) {
        return rules.stream().filter(r -> r.appliesTo(method, currency)).max(Comparator.comparingInt(Rule::specificity));
    }

    /** Expected fee in the line currency, or empty when nothing is configured. */
    public Optional<BigDecimal> expectedFee(String method, String currency, BigDecimal amount) {
        Optional<Rule> rule = ruleFor(method, currency);
        BigDecimal pct = rule.map(Rule::percentage).orElse(fallbackPercentage);
        if (pct == null) {
            return Optional.empty();
        }
        BigDecimal fixed = rule.map(Rule::fixedFee).orElse(BigDecimal.ZERO);
        return Optional.of(amount.abs().multiply(pct).divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP)
                .add(fixed).setScale(2, RoundingMode.HALF_UP));
    }
}
