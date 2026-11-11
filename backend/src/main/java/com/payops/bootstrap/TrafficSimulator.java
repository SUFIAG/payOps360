package com.payops.bootstrap;

import com.payops.identity.application.port.output.OrganizationRepository;
import com.payops.identity.domain.Organization;
import com.payops.payment.application.port.input.PaymentCommandUseCase;
import com.payops.payment.application.port.input.PaymentQueryUseCase;
import com.payops.payment.application.port.input.PaymentView;
import com.payops.payment.domain.PaymentSearchCriteria;
import com.payops.payment.domain.PaymentStatus;
import com.payops.shared.application.Actor;
import com.payops.shared.application.RequestContext;
import com.payops.shared.domain.PageQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

/**
 * Drives live demo traffic through the real application use cases (risk
 * scoring, alerting, live stream). Every 30 minutes the unstable provider
 * degrades for 8 minutes so anomaly detection, incident correlation and AI
 * root-cause analysis can be demonstrated end to end. Development only.
 */
@Slf4j
@Component
@Profile("dev")
@ConditionalOnProperty(prefix = "payops.simulator", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
class TrafficSimulator {

    private static final int TICKS_PER_MINUTE = 20;
    private static final RequestContext CONTEXT = new RequestContext("127.0.0.1", "payops360-simulator");

    private final OrganizationRepository organizations;
    private final PaymentCommandUseCase commands;
    private final PaymentQueryUseCase queries;
    private final Clock clock;
    private final DemoTrafficGenerator generator = new DemoTrafficGenerator(System.nanoTime());

    @Value("${payops.simulator.payments-per-minute:40}")
    private int paymentsPerMinute;

    @Scheduled(fixedRate = 3000, initialDelay = 15000)
    void tick() {
        try {
            for (Organization org : organizations.findAllActive()) {
                Actor actor = Actor.system(org.id());
                ingest(actor);
                progressInFlight(actor);
            }
        } catch (RuntimeException e) {
            log.warn("Simulator tick failed: {}", e.getMessage());
        }
    }

    private void ingest(Actor actor) {
        double expected = paymentsPerMinute * DemoTrafficGenerator.intensity(clock.instant()) / TICKS_PER_MINUTE;
        int count = (int) Math.floor(expected) + (generator.random().nextDouble() < expected % 1 ? 1 : 0);
        double degradation = degradation(clock.instant());
        for (int i = 0; i < count; i++) {
            DemoTrafficGenerator.GeneratedPayment g = generator.next(degradation);
            // Some payments arrive "in flight" and progress over the next ticks.
            boolean inFlight = g.outcome() != PaymentStatus.FAILED && generator.random().nextDouble() < 0.3;
            commands.ingest(actor, new PaymentCommandUseCase.IngestCommand(null, "ORD-" + Long.toString(clock.millis(), 36).toUpperCase(),
                    g.amount(), g.currency(), g.provider().code(), g.customerRef(), g.method(), g.cardLast4(), g.country(),
                    inFlight ? PaymentStatus.AUTHORIZED : g.outcome(), g.failureCode(),
                    g.failureCode() == null ? null : g.failureCode().replace('_', ' ').toLowerCase(), g.latencyMs()), CONTEXT);
        }
    }

    private void progressInFlight(Actor actor) {
        Instant cutoff = clock.instant().minus(Duration.ofSeconds(12));
        PaymentSearchCriteria criteria = new PaymentSearchCriteria(Set.of(PaymentStatus.AUTHORIZED, PaymentStatus.CAPTURED),
                null, null, null, null, null, null, null, null, null, false, cutoff.minus(Duration.ofMinutes(3)), cutoff);
        List<PaymentView> inFlight = queries.search(actor.organizationId(), criteria, PageQuery.of(0, 15)).items();
        for (PaymentView p : inFlight) {
            // ~4% are left alone so stuck-payment detection has something to find.
            if (Math.abs(p.reference().hashCode()) % 25 == 0) {
                continue;
            }
            PaymentStatus next = p.status() == PaymentStatus.AUTHORIZED ? PaymentStatus.CAPTURED : PaymentStatus.SETTLED;
            commands.changeStatus(actor, p.reference(), new PaymentCommandUseCase.StatusChangeCommand(next,
                    "Provider webhook", null, null, null), CONTEXT);
        }
    }

    /** 0 normally; ramps to ~1 for 8 minutes every half hour on the unstable provider. */
    static double degradation(Instant now) {
        int minute = now.atZone(ZoneOffset.UTC).getMinute() % 30;
        if (minute >= 20 && minute < 28) {
            return minute < 22 ? 0.6 : 1.0;
        }
        return 0;
    }
}
