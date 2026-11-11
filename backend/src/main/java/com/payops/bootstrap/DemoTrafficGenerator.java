package com.payops.bootstrap;

import com.payops.payment.domain.PaymentMethod;
import com.payops.payment.domain.PaymentStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Random;

/**
 * Generates statistically plausible payment traffic: diurnal volume curve,
 * corridor-specific amounts, provider-specific success rates, failure-code
 * mixes and log-normal latencies.
 */
final class DemoTrafficGenerator {

    private static final int CUSTOMER_POOL = 1800;

    record GeneratedPayment(DemoCatalog.DemoProvider provider, String customerRef, BigDecimal amount, String currency,
                            String country, PaymentMethod method, String cardLast4, PaymentStatus outcome,
                            String failureCode, int latencyMs) {
    }

    private final Random random;

    DemoTrafficGenerator(long seed) {
        this.random = new Random(seed);
    }

    /** Relative traffic intensity for an hour of day (UTC): low at night, peaks at midday and evening. */
    static double intensity(Instant at) {
        int hour = at.atZone(ZoneOffset.UTC).getHour();
        double daily = 0.55 + 0.45 * Math.sin((hour - 6) / 24.0 * 2 * Math.PI);
        double evening = hour >= 17 && hour <= 21 ? 0.25 : 0;
        return Math.max(0.2, daily + evening);
    }

    GeneratedPayment next(double degradation) {
        DemoCatalog.DemoProvider provider = pickProvider();
        DemoCatalog.Corridor corridor = pickCorridor();
        BigDecimal amount = amount(corridor);
        boolean unstable = provider.code().equals(DemoCatalog.UNSTABLE_PROVIDER) && degradation > 0;
        double success = unstable ? provider.baseSuccess() - 0.45 * degradation : provider.baseSuccess();
        boolean ok = random.nextDouble() < success;
        String failureCode = null;
        PaymentStatus outcome;
        if (ok) {
            double roll = random.nextDouble();
            outcome = provider.method() == PaymentMethod.CARD && roll < 0.25 ? PaymentStatus.CAPTURED
                    : roll < 0.08 ? PaymentStatus.PROCESSING : PaymentStatus.SETTLED;
        } else {
            outcome = PaymentStatus.FAILED;
            failureCode = unstable && random.nextDouble() < 0.8 ? "PROCESSOR_TIMEOUT"
                    : provider.failureCodes().get(random.nextInt(provider.failureCodes().size()));
        }
        double latencyFactor = unstable ? 1 + 2.5 * degradation : 1;
        int latency = (int) Math.max(60, provider.typicalLatencyMs() * latencyFactor * Math.exp(random.nextGaussian() * 0.35));
        String last4 = provider.method() == PaymentMethod.CARD ? String.format("%04d", random.nextInt(10000)) : null;
        return new GeneratedPayment(provider, customer(), amount, corridor.currency(), corridor.country(),
                provider.method(), last4, outcome, failureCode, latency);
    }

    private String customer() {
        // Pareto-like reuse: a few customers send often, most occasionally.
        int index = (int) Math.min(CUSTOMER_POOL - 1, Math.abs(random.nextGaussian()) * CUSTOMER_POOL / 2.5);
        return "CUS-" + (100_000 + index);
    }

    private BigDecimal amount(DemoCatalog.Corridor corridor) {
        double logMin = Math.log(corridor.minAmount());
        double logMax = Math.log(corridor.maxAmount());
        double value = Math.exp(logMin + (logMax - logMin) * Math.pow(random.nextDouble(), 1.6));
        if (random.nextDouble() < 0.06) {
            value = Math.round(value / 100) * 100.0;
        }
        if (random.nextDouble() < 0.004) {
            value = value * (6 + random.nextInt(10));
        }
        return BigDecimal.valueOf(Math.max(corridor.minAmount(), value)).setScale(2, RoundingMode.HALF_UP);
    }

    private DemoCatalog.DemoProvider pickProvider() {
        List<DemoCatalog.DemoProvider> providers = DemoCatalog.PROVIDERS;
        int total = providers.stream().mapToInt(DemoCatalog.DemoProvider::weight).sum();
        int roll = random.nextInt(total);
        for (DemoCatalog.DemoProvider p : providers) {
            roll -= p.weight();
            if (roll < 0) {
                return p;
            }
        }
        return providers.get(0);
    }

    private DemoCatalog.Corridor pickCorridor() {
        List<DemoCatalog.Corridor> corridors = DemoCatalog.CORRIDORS;
        int total = corridors.stream().mapToInt(DemoCatalog.Corridor::weight).sum();
        int roll = random.nextInt(total);
        for (DemoCatalog.Corridor c : corridors) {
            roll -= c.weight();
            if (roll < 0) {
                return c;
            }
        }
        return corridors.get(0);
    }

    Random random() {
        return random;
    }
}
