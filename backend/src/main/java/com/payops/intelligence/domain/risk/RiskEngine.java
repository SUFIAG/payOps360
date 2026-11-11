package com.payops.intelligence.domain.risk;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Explainable real-time risk model. Each rule contributes points and a
 * human-readable reason, so analysts (and regulators) can see exactly why a
 * payment was scored the way it was. Weights are calibrated for remittance
 * and cross-border payment patterns.
 */
public final class RiskEngine {

    private static final BigDecimal LARGE_FIRST_PAYMENT_USD = new BigDecimal("2000");
    private static final BigDecimal REPORTING_THRESHOLD_USD = new BigDecimal("10000");
    /** Corridors under enhanced monitoring; configurable per tenant in a later release. */
    private static final Set<String> ENHANCED_MONITORING = Set.of("NG", "IR", "KP", "MM", "SY", "YE", "AF", "VE");

    private RiskEngine() {
    }

    public static RiskScore score(RiskSignals s) {
        int points = 0;
        List<String> factors = new ArrayList<>();
        BigDecimal amount = s.amountUsd();

        if (amount.compareTo(REPORTING_THRESHOLD_USD) >= 0) {
            points += 20;
            factors.add("Amount ≥ 10,000 USD (regulatory reporting threshold)");
        }
        if (s.isNewCustomer()) {
            if (amount.compareTo(LARGE_FIRST_PAYMENT_USD) >= 0) {
                points += 25;
                factors.add("Large first payment from a new customer");
            } else {
                points += 5;
                factors.add("New customer with no payment history");
            }
        } else if (s.customerAverageUsd().signum() > 0) {
            BigDecimal ratio = amount.divide(s.customerAverageUsd(), 1, RoundingMode.HALF_UP);
            if (ratio.compareTo(BigDecimal.valueOf(5)) >= 0) {
                points += 25;
                factors.add("Amount is " + ratio + "× the customer's 30-day average");
            } else if (ratio.compareTo(BigDecimal.valueOf(3)) >= 0) {
                points += 12;
                factors.add("Amount is " + ratio + "× the customer's 30-day average");
            }
        }
        if (s.customerPaymentsLastHour() >= 5) {
            points += 20;
            factors.add(s.customerPaymentsLastHour() + " payments in the last hour (velocity)");
        } else if (s.customerPaymentsLastHour() >= 3) {
            points += 10;
            factors.add(s.customerPaymentsLastHour() + " payments in the last hour");
        }
        if (s.customerFailuresLast24h() >= 3) {
            points += 20;
            factors.add(s.customerFailuresLast24h() + " failed attempts in 24h (possible card testing)");
        } else if (s.customerFailuresLast24h() >= 1) {
            points += 5;
            factors.add("Recent failed attempt in the last 24h");
        }
        if (s.customerCountriesLast24h() >= 3) {
            points += 15;
            factors.add("Activity from " + s.customerCountriesLast24h() + " countries in 24h");
        }
        if (s.country() != null && ENHANCED_MONITORING.contains(s.country())) {
            points += 15;
            factors.add("Destination under enhanced monitoring (" + s.country() + ")");
        }
        if (s.hourOfDayUtc() >= 0 && s.hourOfDayUtc() < 4) {
            points += 5;
            factors.add("Unusual time of day (00:00–04:00 UTC)");
        }
        if (amount.compareTo(new BigDecimal("1000")) >= 0
                && amount.remainder(new BigDecimal("1000")).signum() == 0) {
            points += 5;
            factors.add("Round-number amount");
        }

        int score = Math.min(points, 100);
        return new RiskScore(score, level(score), factors);
    }

    static String level(int score) {
        if (score >= 80) {
            return "CRITICAL";
        }
        if (score >= 60) {
            return "HIGH";
        }
        if (score >= 35) {
            return "MEDIUM";
        }
        return "LOW";
    }
}
