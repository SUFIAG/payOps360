package com.payops.intelligence.domain;

import com.payops.intelligence.domain.anomaly.RobustAnomalyDetector;
import com.payops.intelligence.domain.failure.FailureCategory;
import com.payops.intelligence.domain.failure.FailureKnowledgeBase;
import com.payops.intelligence.domain.forecast.VolumeForecaster;
import com.payops.intelligence.domain.risk.RiskEngine;
import com.payops.intelligence.domain.risk.RiskScore;
import com.payops.intelligence.domain.risk.RiskSignals;
import com.payops.intelligence.domain.routing.RoutingAdvisor;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IntelligenceDomainTest {

    @Test
    void routineRepeatPaymentIsLowRisk() {
        RiskScore score = RiskEngine.score(new RiskSignals(new BigDecimal("300"), new BigDecimal("280"), 12, 0, 0, 1,
                "BANK_TRANSFER", "PK", 14));
        assertThat(score.level()).isEqualTo("LOW");
        assertThat(score.factors()).isEmpty();
    }

    @Test
    void cardTestingPatternIsCriticalAndExplained() {
        RiskScore score = RiskEngine.score(new RiskSignals(new BigDecimal("12000"), new BigDecimal("150"), 8, 6, 4, 3,
                "CARD", "NG", 2));
        assertThat(score.level()).isEqualTo("CRITICAL");
        assertThat(score.score()).isLessThanOrEqualTo(100);
        assertThat(score.factors()).anyMatch(f -> f.contains("velocity"))
                .anyMatch(f -> f.contains("card testing"))
                .anyMatch(f -> f.contains("10,000 USD"));
    }

    @Test
    void largeFirstPaymentRaisesRisk() {
        RiskScore score = RiskEngine.score(new RiskSignals(new BigDecimal("5000"), BigDecimal.ZERO, 0, 0, 0, 1,
                "CARD", "GB", 12));
        assertThat(score.score()).isGreaterThanOrEqualTo(25);
        assertThat(score.factors()).anyMatch(f -> f.contains("new customer"));
    }

    @Test
    void robustDetectorFlagsSpikeButIgnoresNoise() {
        List<Double> baseline = List.of(2.0, 3.0, 2.5, 3.5, 2.0, 2.8, 3.1, 40.0, 2.6, 3.0);
        assertThat(RobustAnomalyDetector.detect(baseline, 3.4, RobustAnomalyDetector.Direction.HIGH, 3.5, 5)).isEmpty();
        var finding = RobustAnomalyDetector.detect(baseline, 35, RobustAnomalyDetector.Direction.HIGH, 3.5, 5);
        assertThat(finding).isPresent();
        assertThat(finding.get().baselineMedian()).isBetween(2.5, 3.1);
        assertThat(RobustAnomalyDetector.detect(List.of(1.0, 2.0), 50, RobustAnomalyDetector.Direction.HIGH, 3.5, 5)).isEmpty();
    }

    @Test
    void robustDetectorFindsVolumeDrops() {
        List<Double> volume = List.of(100.0, 96.0, 104.0, 99.0, 101.0, 98.0, 103.0);
        assertThat(RobustAnomalyDetector.detect(volume, 20, RobustAnomalyDetector.Direction.LOW, 3.5, 20)).isPresent();
        assertThat(RobustAnomalyDetector.detect(volume, 95, RobustAnomalyDetector.Direction.LOW, 3.5, 20)).isEmpty();
    }

    @Test
    void forecastFollowsDailySeasonality() {
        List<Double> hourly = new ArrayList<>();
        for (int day = 0; day < 3; day++) {
            for (int h = 0; h < 24; h++) {
                hourly.add(h >= 9 && h <= 18 ? 200.0 : 50.0);
            }
        }
        Instant lastHour = Instant.parse("2026-10-07T23:00:00Z");
        List<VolumeForecaster.Point> points = VolumeForecaster.forecast(hourly, lastHour, 12);
        assertThat(points).hasSize(12);
        assertThat(points.get(0).at()).isEqualTo(Instant.parse("2026-10-08T00:00:00Z"));
        double night = points.get(2).expected();
        double day = points.get(10).expected();
        assertThat(day).isGreaterThan(night);
        assertThat(points).allMatch(p -> p.lower() <= p.expected() && p.expected() <= p.upper() && p.lower() >= 0);
    }

    @Test
    void routingPrefersReliableAffordableHealthyProviders() {
        var ranking = RoutingAdvisor.rank(List.of(
                new RoutingAdvisor.Candidate("a", "Alpha", 500, 98.5, 400, 97, 1200, new BigDecimal("20"), true),
                new RoutingAdvisor.Candidate("b", "Beta", 500, 91.0, 900, 97, 1200, new BigDecimal("30"), true),
                new RoutingAdvisor.Candidate("c", "Gamma", 500, 98.7, 380, 97, 1200, new BigDecimal("22"), false)));
        assertThat(ranking.get(0).code()).isEqualTo("a");
        assertThat(ranking.get(2).code()).isEqualTo("c");
        assertThat(ranking.get(2).rationale()).contains("degraded");
    }

    @Test
    void failureKnowledgeClassifiesCodes() {
        assertThat(FailureKnowledgeBase.lookup("processor_timeout").category()).isEqualTo(FailureCategory.TECHNICAL);
        assertThat(FailureKnowledgeBase.lookup("CARD_EXPIRED").retryable()).isFalse();
        assertThat(FailureKnowledgeBase.lookup("NEW_CODE_42").category()).isEqualTo(FailureCategory.UNKNOWN);
        assertThat(FailureKnowledgeBase.lookup(null).code()).isEqualTo("UNSPECIFIED");
    }
}
