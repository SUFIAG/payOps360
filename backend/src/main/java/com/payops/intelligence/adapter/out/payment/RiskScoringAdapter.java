package com.payops.intelligence.adapter.out.payment;

import com.payops.analytics.domain.FxRates;
import com.payops.intelligence.application.port.out.CustomerBehaviorPort;
import com.payops.intelligence.domain.risk.RiskEngine;
import com.payops.intelligence.domain.risk.RiskScore;
import com.payops.intelligence.domain.risk.RiskSignals;
import com.payops.payment.application.port.out.RiskScoringPort;
import com.payops.payment.domain.RiskAssessment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;

/**
 * Implements the payment module's risk port with the intelligence module's
 * explainable risk engine. Scoring never blocks ingestion: on any error the
 * payment is accepted with an "unscored" marker for later review.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class RiskScoringAdapter implements RiskScoringPort {

    private final CustomerBehaviorPort behavior;

    @Override
    public RiskAssessment assess(RiskInput input) {
        try {
            CustomerBehaviorPort.CustomerProfile profile = behavior.profile(input.organizationId(), input.customerRef(), input.at());
            RiskScore score = RiskEngine.score(new RiskSignals(
                    FxRates.toUsd(input.amount(), input.currency()),
                    profile.averageUsd30d(),
                    profile.payments30d(),
                    profile.paymentsLastHour(),
                    profile.failures24h(),
                    profile.countries24h(),
                    input.method().name(),
                    input.country(),
                    input.at().atZone(ZoneOffset.UTC).getHour()));
            return new RiskAssessment(score.score(), RiskAssessment.RiskLevel.valueOf(score.level()), score.factors());
        } catch (RuntimeException e) {
            log.warn("Risk scoring unavailable, accepting payment unscored: {}", e.getMessage());
            return new RiskAssessment(0, RiskAssessment.RiskLevel.LOW, java.util.List.of("Risk scoring unavailable"));
        }
    }
}
