package com.payops.notification.application.service;

import com.payops.notification.application.port.in.EscalationUseCase;
import com.payops.notification.application.port.out.EscalationProgressRepository;
import com.payops.notification.application.port.out.EscalationSubjectsPort;
import com.payops.notification.application.port.out.NotificationConfigRepository;
import com.payops.notification.domain.EscalationPolicy;
import com.payops.notification.domain.NotificationChannel;
import com.payops.notification.domain.NotificationDelivery;
import com.payops.notification.domain.NotificationMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Walks unacknowledged incidents and critical alerts through the tenant's
 * escalation policy, one level per run at most. The most specific policy
 * (highest minimum severity) that applies to the subject wins. Each step is
 * recorded (progress table + incident timeline) in the same transaction as
 * the queued deliveries, so a level is never notified twice.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EscalationService implements EscalationUseCase {

    private static final int BATCH = 500;

    private final NotificationConfigRepository config;
    private final EscalationProgressRepository progress;
    private final EscalationSubjectsPort subjects;
    private final NotificationDispatchService dispatcher;
    private final ChannelDeliverySettings settings;
    private final Clock clock;

    @Override
    @Transactional
    public int escalateDue() {
        Map<Long, List<EscalationPolicy>> policiesByOrg = config.enabledPolicies().stream()
                .sorted(Comparator.comparing(EscalationPolicy::minSeverity).reversed())
                .collect(Collectors.groupingBy(EscalationPolicy::organizationId));
        if (policiesByOrg.isEmpty()) {
            return 0;
        }
        Instant now = clock.instant();
        int steps = 0;
        for (EscalationSubjectsPort.Subject subject : subjects.awaitingAcknowledgement(BATCH)) {
            Optional<EscalationPolicy> policy = policiesByOrg.getOrDefault(subject.organizationId(), List.of()).stream()
                    .filter(p -> p.appliesTo(subject.severity()))
                    .findFirst();
            if (policy.isPresent() && escalate(subject, policy.get(), now)) {
                steps++;
            }
        }
        return steps;
    }

    private boolean escalate(EscalationSubjectsPort.Subject subject, EscalationPolicy policy, Instant now) {
        Optional<EscalationProgressRepository.Progress> current = progress.find(subject.organizationId(),
                subject.type(), subject.reference());
        // A different policy took over (e.g. after a severity change): continue from the level reached.
        int notified = current.map(EscalationProgressRepository.Progress::level).orElse(0);
        Instant last = current.map(EscalationProgressRepository.Progress::lastEscalatedAt).orElse(subject.openedAt());
        Optional<EscalationPolicy.Level> due = policy.dueLevel(notified, last, now);
        if (due.isEmpty()) {
            return false;
        }
        EscalationPolicy.Level level = due.get();
        progress.save(subject.organizationId(), subject.type(), subject.reference(), policy.id(), level.number(), now);

        long minutes = Duration.between(subject.openedAt(), now).toMinutes();
        NotificationMessage message = new NotificationMessage("ESCALATION", subject.severity(), subject.type(),
                subject.reference(),
                "[" + subject.severity() + "] Escalation level " + level.number() + ": " + subject.title(),
                subject.type().charAt(0) + subject.type().substring(1).toLowerCase() + " " + subject.reference()
                        + " is still unacknowledged after " + minutes + " min (policy \"" + policy.name() + "\")."
                        + (subject.providerCode() == null ? "" : "\nProvider: " + subject.providerCode()),
                NotificationDispatchService.link(settings.consoleBaseUrl(), subject.type(), subject.reference()), now);
        int queued = dispatcher.enqueue(subject.organizationId(), level.channelIds(), NotificationDelivery.Source.ESCALATION,
                "ESCALATION:" + subject.reference() + ":L" + level.number(), message);

        if ("INCIDENT".equals(subject.type())) {
            String channels = config.channelsById(subject.organizationId(), level.channelIds()).values().stream()
                    .map(NotificationChannel::name).sorted().collect(Collectors.joining(", "));
            subjects.recordIncidentEscalation(subject.organizationId(), subject.reference(), level.number(),
                    "Escalation level " + level.number() + " (" + policy.name() + ") notified: "
                            + (channels.isEmpty() ? "no active channels" : channels)
                            + " - unacknowledged for " + minutes + " min");
        }
        log.info("Escalated {} {} to level {} ({} deliveries)", subject.type(), subject.reference(), level.number(),
                queued);
        return true;
    }
}
