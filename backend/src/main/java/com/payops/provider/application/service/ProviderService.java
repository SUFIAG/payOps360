package com.payops.provider.application.service;

import com.payops.audit.application.port.in.AuditTrail;
import com.payops.audit.domain.AuditAction;
import com.payops.audit.domain.AuditEntry;
import com.payops.audit.domain.AuditOutcome;
import com.payops.provider.application.port.in.ProviderCatalogUseCase;
import com.payops.provider.application.port.in.ProviderHealthUseCase;
import com.payops.provider.application.port.in.ProviderView;
import com.payops.provider.application.port.out.HealthSnapshotRepository;
import com.payops.provider.application.port.out.ProviderRepository;
import com.payops.provider.application.port.out.TrafficStatisticsPort;
import com.payops.provider.domain.HealthSnapshot;
import com.payops.provider.domain.HealthStatus;
import com.payops.provider.domain.Provider;
import com.payops.provider.domain.TrafficWindow;
import com.payops.shared.application.Actor;
import com.payops.shared.application.LiveEventPublisher;
import com.payops.shared.application.OperationalEvent;
import com.payops.shared.application.OperationalEventPublisher;
import com.payops.shared.domain.Severity;
import com.payops.shared.application.RequestContext;
import com.payops.shared.domain.ConflictException;
import com.payops.shared.domain.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class ProviderService implements ProviderCatalogUseCase, ProviderHealthUseCase {

    static final Duration HEALTH_WINDOW = Duration.ofMinutes(15);

    private final ProviderRepository providers;
    private final HealthSnapshotRepository snapshots;
    private final TrafficStatisticsPort traffic;
    private final LiveEventPublisher liveEvents;
    private final OperationalEventPublisher operationalEvents;
    private final AuditTrail auditTrail;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<ProviderView> list(long organizationId) {
        List<Provider> all = providers.findByOrganization(organizationId);
        Map<Long, HealthSnapshot> health = snapshots.latestFor(all.stream().map(Provider::id).toList());
        return all.stream().map(p -> ProviderView.of(p, health.get(p.id()))).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ProviderView get(long organizationId, String code) {
        return findByCode(organizationId, code).orElseThrow(() -> new NotFoundException("Provider", code));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProviderView> findByCode(long organizationId, String code) {
        return providers.findByCode(organizationId, code).map(this::withHealth);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProviderView> findById(long organizationId, long providerId) {
        return providers.findById(organizationId, providerId).map(this::withHealth);
    }

    @Override
    public ProviderView register(Actor actor, ProviderDefinition d, RequestContext context) {
        if (providers.existsByCode(actor.organizationId(), d.code())) {
            throw new ConflictException("PROVIDER_EXISTS", "A provider with code '" + d.code() + "' already exists");
        }
        Provider saved = providers.save(new Provider(null, actor.organizationId(), d.code(), d.name(), d.type(),
                d.region(), d.slaLatencyMs(), d.slaSuccessRate(), d.costBps(), true));
        audit(actor, AuditAction.PROVIDER_CREATED, saved, context);
        return ProviderView.of(saved, null);
    }

    @Override
    public ProviderView update(Actor actor, String code, ProviderDefinition d, RequestContext context) {
        Provider provider = load(actor.organizationId(), code);
        provider.update(d.name(), d.type(), d.region(), d.slaLatencyMs(), d.slaSuccessRate(), d.costBps());
        Provider saved = providers.save(provider);
        audit(actor, AuditAction.PROVIDER_UPDATED, saved, context);
        return withHealth(saved);
    }

    @Override
    public ProviderView setActive(Actor actor, String code, boolean active, RequestContext context) {
        Provider provider = load(actor.organizationId(), code);
        if (active) {
            provider.activate();
        } else {
            provider.deactivate();
        }
        Provider saved = providers.save(provider);
        audit(actor, AuditAction.PROVIDER_UPDATED, saved, context);
        return withHealth(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<HealthSnapshot> history(long organizationId, String code, Duration lookback) {
        Provider provider = load(organizationId, code);
        return snapshots.history(provider.id(), clock.instant().minus(lookback));
    }

    @Override
    public int captureSnapshots() {
        Instant now = clock.instant();
        Map<Long, TrafficWindow> windows = traffic.trafficByProvider(now.minus(HEALTH_WINDOW), now).stream()
                .collect(Collectors.toMap(TrafficWindow::providerId, Function.identity()));
        List<HealthSnapshot> captured = new ArrayList<>();
        List<Provider> active = providers.findAllActive();
        Map<Long, HealthSnapshot> previous = active.isEmpty() ? Map.of()
                : snapshots.latestFor(active.stream().map(Provider::id).toList());
        for (Provider provider : active) {
            TrafficWindow window = windows.getOrDefault(provider.id(),
                    new TrafficWindow(provider.id(), now.minus(HEALTH_WINDOW), now, 0, 0, 0, 0, 0));
            HealthSnapshot snapshot = HealthSnapshot.evaluate(provider, window);
            captured.add(snapshot);
            liveEvents.publish(provider.organizationId(), "provider.health", ProviderView.of(provider, snapshot));
            HealthSnapshot before = previous.get(provider.id());
            if (worsened(before == null ? HealthStatus.UNKNOWN : before.status(), snapshot.status())) {
                operationalEvents.publish(new OperationalEvent(provider.organizationId(),
                        OperationalEvent.Type.PROVIDER_DEGRADED,
                        snapshot.status() == HealthStatus.CRITICAL ? Severity.CRITICAL : Severity.HIGH, "PROVIDER",
                        provider.code(), provider.code(), provider.name() + " is " + snapshot.status().name().toLowerCase(),
                        String.format("Success rate %s%% (SLA %s%%), p95 latency %d ms over the last %d min",
                                snapshot.successRate(), provider.slaSuccessRate(), snapshot.p95LatencyMs(),
                                HEALTH_WINDOW.toMinutes()),
                        "PROVIDER_DEGRADED:" + provider.code() + ":" + snapshot.status().name(), now));
            }
        }
        snapshots.saveAll(captured);
        return captured.size();
    }

    /** HEALTHY/UNKNOWN -> DEGRADED/CRITICAL, or DEGRADED -> CRITICAL. */
    private static boolean worsened(HealthStatus before, HealthStatus after) {
        boolean bad = after == HealthStatus.DEGRADED || after == HealthStatus.CRITICAL;
        return bad && before != after && before != HealthStatus.CRITICAL;
    }

    private ProviderView withHealth(Provider provider) {
        return ProviderView.of(provider, snapshots.latestFor(List.of(provider.id())).get(provider.id()));
    }

    private Provider load(long organizationId, String code) {
        return providers.findByCode(organizationId, code).orElseThrow(() -> new NotFoundException("Provider", code));
    }

    private void audit(Actor actor, AuditAction action, Provider provider, RequestContext context) {
        auditTrail.record(new AuditEntry(actor.organizationId(), actor.userId(), actor.email(), action, "PROVIDER",
                provider.code(), AuditOutcome.SUCCESS, context.ipAddress(), context.userAgent(),
                Map.of("active", String.valueOf(provider.active())), clock.instant()));
    }
}
