package com.payops.payment.domain;

import java.util.List;

/**
 * Explainable risk verdict: a 0-100 score, its band and the human readable
 * factors that contributed to it.
 */
public record RiskAssessment(int score, RiskLevel level, List<String> factors) {

    public RiskAssessment {
        score = Math.max(0, Math.min(100, score));
        factors = List.copyOf(factors);
    }

    public static RiskAssessment none() {
        return new RiskAssessment(0, RiskLevel.LOW, List.of());
    }

    public enum RiskLevel {
        LOW, MEDIUM, HIGH, CRITICAL;

        public static RiskLevel fromScore(int score) {
            if (score >= 80) {
                return CRITICAL;
            }
            if (score >= 60) {
                return HIGH;
            }
            if (score >= 35) {
                return MEDIUM;
            }
            return LOW;
        }
    }
}
