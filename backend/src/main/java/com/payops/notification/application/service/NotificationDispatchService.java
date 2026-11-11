package com.payops.notification.application.service;

import com.payops.notification.application.port.input.EmailSender;
import com.payops.notification.application.port.input.NotificationDispatchUseCase;
import com.payops.notification.application.port.output.ChannelTransport;
import com.payops.notification.application.port.output.NotificationConfigRepository;
import com.payops.notification.application.port.output.NotificationDeliveryRepository;
import com.payops.notification.domain.BrandedEmail;
import com.payops.notification.domain.ChannelTarget;
import com.payops.notification.domain.ChannelType;
import com.payops.notification.domain.EmailMessage;
import com.payops.notification.domain.NotificationChannel;
import com.payops.notification.domain.NotificationDelivery;
import com.payops.notification.domain.NotificationMessage;
import com.payops.notification.domain.NotificationRule;
import com.payops.shared.application.OperationalEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Routing and the generic delivery outbox. Routing writes one outbox row per
 * (rule, channel) inside its own transaction; delivery runs outside any
 * transaction so each attempt's outcome is persisted on its own. Failures are
 * retried with exponential back-off up to {@link NotificationDelivery#MAX_ATTEMPTS}.
 * E-mail channels hand off to the transactional e-mail outbox.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationDispatchService implements NotificationDispatchUseCase {

    private static final int MAX_ERROR_LENGTH = 1000;
    private static final Duration DEDUP_RETENTION = Duration.ofDays(1);

    private final NotificationConfigRepository config;
    private final NotificationDeliveryRepository deliveries;
    private final ChannelTransport transport;
    private final EmailSender emailSender;
    private final ChannelDeliverySettings settings;
    private final Clock clock;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int route(OperationalEvent event) {
        List<NotificationRule> rules = config.enabledRules(event.organizationId(), event.type().name()).stream()
                .filter(r -> r.matches(event.type().name(), event.severity(), event.providerCode()))
                .toList();
        if (rules.isEmpty()) {
            return 0;
        }
        Instant now = clock.instant();
        Set<Long> channelIds = new LinkedHashSet<>();
        rules.forEach(r -> channelIds.addAll(r.channelIds()));
        Map<Long, NotificationChannel> channels = config.channelsById(event.organizationId(), channelIds);
        NotificationMessage message = message(event);
        int queued = 0;
        for (NotificationRule rule : rules) {
            if (!deliveries.claimDedup(rule.id(), event.dedupKey(), now, NotificationRule.QUIET_WINDOW)) {
                log.debug("Suppressed duplicate {} for rule {} (quiet window)", event.dedupKey(), rule.publicId());
                continue;
            }
            for (Long channelId : rule.channelIds()) {
                NotificationChannel channel = channels.get(channelId);
                if (channel != null && channel.enabled()) {
                    deliveries.enqueue(channel, rule.id(), NotificationDelivery.Source.RULE, event.dedupKey(), message,
                            now);
                    queued++;
                }
            }
        }
        return queued;
    }

    /** Queues a message to the given channels (used by the escalation engine, joins its transaction). */
    @Transactional
    public int enqueue(long organizationId, List<Long> channelIds, NotificationDelivery.Source source, String eventKey,
                       NotificationMessage message) {
        Instant now = clock.instant();
        int queued = 0;
        for (NotificationChannel channel : config.channelsById(organizationId, channelIds).values()) {
            if (channel.enabled()) {
                deliveries.enqueue(channel, null, source, eventKey, message, now);
                queued++;
            }
        }
        return queued;
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public int deliverDue() {
        Instant now = clock.instant();
        List<NotificationDelivery> due = deliveries.findDue(now, settings.batchSize());
        for (NotificationDelivery delivery : due) {
            deliver(delivery, now);
        }
        deliveries.purgeDedup(now.minus(DEDUP_RETENTION));
        return due.size();
    }

    /**
     * Sends immediately, bypassing the outbox (channel tests).
     *
     * @return null on success, otherwise a short error description
     */
    public String sendNow(NotificationChannel channel, NotificationMessage message, UUID deliveryId) {
        try {
            send(channel, message, deliveryId);
            return null;
        } catch (RuntimeException ex) {
            return describe(ex);
        }
    }

    private void deliver(NotificationDelivery delivery, Instant now) {
        NotificationChannel channel = delivery.channelId() == null ? null
                : config.channelsById(delivery.organizationId(), List.of(delivery.channelId())).get(delivery.channelId());
        if (channel == null || !channel.enabled()) {
            deliveries.markAttemptFailed(delivery.id(), channel == null ? "Channel was deleted" : "Channel is disabled",
                    now, true);
            return;
        }
        try {
            send(channel, delivery.message(), delivery.publicId());
            deliveries.markSent(delivery.id(), now);
        } catch (RuntimeException ex) {
            int attempts = delivery.attempts() + 1;
            boolean terminal = attempts >= NotificationDelivery.MAX_ATTEMPTS;
            deliveries.markAttemptFailed(delivery.id(), describe(ex),
                    now.plus(NotificationDelivery.backoffAfter(attempts)), terminal);
            log.warn("Notification delivery failed id={} channel={} type={} attempt={}/{}{}: {}",
                    delivery.publicId(), channel.publicId(), channel.type(), attempts, NotificationDelivery.MAX_ATTEMPTS,
                    terminal ? " (giving up)" : "", describe(ex));
        }
    }

    private void send(NotificationChannel channel, NotificationMessage message, UUID deliveryId) {
        ChannelTarget target = config.target(channel.id());
        if (channel.type() == ChannelType.EMAIL) {
            for (String address : target.emailAddresses()) {
                emailSender.send(new EmailMessage(channel.organizationId(), address, subject(message), "NOTIFICATION",
                        email(message)));
            }
            return;
        }
        transport.send(channel.type(), target, message, deliveryId);
    }

    private NotificationMessage message(OperationalEvent event) {
        String headline = switch (event.type()) {
            case ALERT_RAISED -> "Alert raised";
            case INCIDENT_OPENED -> "Incident opened";
            case INCIDENT_ESCALATED -> "Incident escalated";
            case INCIDENT_SLA_BREACHED -> "Incident SLA breached";
            case INCIDENT_RESOLVED -> "Incident resolved";
            case PROVIDER_DEGRADED -> "Provider degraded";
            case RECONCILIATION_EXCEPTIONS -> "Reconciliation exceptions";
        };
        String body = (event.detail() == null ? "" : event.detail())
                + (event.providerCode() == null ? "" : "\nProvider: " + event.providerCode());
        return new NotificationMessage(event.type().name(), event.severity(), event.subjectType(),
                event.subjectReference(), "[" + event.severity() + "] " + headline + ": " + event.title(), body.trim(),
                link(settings.consoleBaseUrl(), event.subjectType(), event.subjectReference()), event.occurredAt());
    }

    static String link(String baseUrl, String subjectType, String reference) {
        if (subjectType == null) {
            return baseUrl;
        }
        return switch (subjectType) {
            case "INCIDENT" -> baseUrl + "/incidents/" + URLEncoder.encode(reference, StandardCharsets.UTF_8);
            case "ALERT" -> baseUrl + "/alerts";
            case "PROVIDER" -> baseUrl + "/providers";
            case "RECONCILIATION" -> baseUrl + "/reconciliation";
            default -> baseUrl;
        };
    }

    private static String subject(NotificationMessage message) {
        String title = message.title();
        return title.length() <= 200 ? title : title.substring(0, 197) + "...";
    }

    private static com.payops.notification.domain.EmailContent email(NotificationMessage message) {
        BrandedEmail email = BrandedEmail.titled(message.title());
        for (String line : message.body().split("\n")) {
            if (!line.isBlank()) {
                email.paragraph(line);
            }
        }
        if (message.link() != null) {
            email.action("Open in PayOps360", message.link());
        }
        return email.footnote("You receive this because a PayOps360 notification rule routes "
                + message.eventType() + " events to this address.").render();
    }

    private static String describe(RuntimeException ex) {
        String text = ex.getClass().getSimpleName() + (ex.getMessage() == null ? "" : ": " + ex.getMessage());
        return text.length() <= MAX_ERROR_LENGTH ? text : text.substring(0, MAX_ERROR_LENGTH);
    }
}
