package com.payops.notification.application.service;

import com.payops.audit.application.port.input.AuditTrail;
import com.payops.audit.domain.AuditAction;
import com.payops.audit.domain.AuditEntry;
import com.payops.audit.domain.AuditOutcome;
import com.payops.notification.application.port.input.NotificationAdminUseCase;
import com.payops.notification.application.port.output.NotificationConfigRepository;
import com.payops.notification.application.port.output.NotificationDeliveryRepository;
import com.payops.notification.domain.ChannelTarget;
import com.payops.notification.domain.ChannelType;
import com.payops.notification.domain.EscalationPolicy;
import com.payops.notification.domain.NotificationChannel;
import com.payops.notification.domain.NotificationDelivery;
import com.payops.notification.domain.NotificationMessage;
import com.payops.notification.domain.NotificationRule;
import com.payops.shared.application.Actor;
import com.payops.shared.application.RequestContext;
import com.payops.shared.domain.ConflictException;
import com.payops.shared.domain.DomainException;
import com.payops.shared.domain.NotFoundException;
import com.payops.shared.domain.Severity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class NotificationAdminService implements NotificationAdminUseCase {

    private final NotificationConfigRepository config;
    private final NotificationDeliveryRepository deliveries;
    private final NotificationDispatchService dispatcher;
    private final ChannelDeliverySettings settings;
    private final AuditTrail auditTrail;
    private final Clock clock;

    // ------------------------------------------------------------ channels

    @Override
    @Transactional(readOnly = true)
    public List<ChannelView> channels(long organizationId) {
        return config.channels(organizationId).stream().map(NotificationAdminService::view).toList();
    }

    @Override
    public ChannelView createChannel(Actor actor, ChannelCommand c, RequestContext context) {
        String name = requireName(c.name());
        if (c.type() == null) {
            throw new DomainException("INVALID_CHANNEL", "Channel type is required");
        }
        ensureUniqueName(actor.organizationId(), name, null);
        ChannelTarget target = secureTarget(c.type(), ChannelTarget.of(c.type(), c.target(), c.signingSecret()));
        NotificationChannel channel = config.insertChannel(actor.organizationId(), name, c.type(), c.enabled(), target,
                actor.email(), clock.instant());
        audit(actor, AuditAction.NOTIFICATION_CHANNEL_CHANGED, "NOTIFICATION_CHANNEL", channel.publicId(), context,
                Map.of("operation", "CREATED", "type", c.type().name()));
        return view(channel);
    }

    @Override
    public ChannelView updateChannel(Actor actor, UUID channelId, ChannelCommand c, RequestContext context) {
        NotificationChannel channel = loadChannel(actor.organizationId(), channelId);
        String name = requireName(c.name());
        ensureUniqueName(actor.organizationId(), name, channel.id());
        ChannelTarget target = null;
        boolean newTarget = c.target() != null && !c.target().isBlank();
        boolean newSecret = c.signingSecret() != null && !c.signingSecret().isBlank();
        if (newTarget || newSecret) {
            ChannelTarget current = config.target(channel.id());
            target = secureTarget(channel.type(), ChannelTarget.of(channel.type(),
                    newTarget ? c.target() : current.target(), newSecret ? c.signingSecret() : current.signingSecret()));
        }
        NotificationChannel updated = config.updateChannel(channel, name, c.enabled(), target, clock.instant());
        audit(actor, AuditAction.NOTIFICATION_CHANNEL_CHANGED, "NOTIFICATION_CHANNEL", channelId, context,
                Map.of("operation", "UPDATED", "targetChanged", String.valueOf(newTarget),
                        "secretRotated", String.valueOf(newSecret)));
        return view(updated);
    }

    @Override
    public void deleteChannel(Actor actor, UUID channelId, RequestContext context) {
        NotificationChannel channel = loadChannel(actor.organizationId(), channelId);
        config.deleteChannel(actor.organizationId(), channel.id());
        audit(actor, AuditAction.NOTIFICATION_CHANNEL_CHANGED, "NOTIFICATION_CHANNEL", channelId, context,
                Map.of("operation", "DELETED", "type", channel.type().name()));
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public TestResult testChannel(Actor actor, UUID channelId, RequestContext context) {
        NotificationChannel channel = loadChannel(actor.organizationId(), channelId);
        Instant now = clock.instant();
        NotificationMessage message = new NotificationMessage("TEST", Severity.LOW, null, null,
                "PayOps360 test notification",
                "This is a test message for channel \"" + channel.name() + "\", sent by " + actor.email()
                        + ". If you can read this, the channel is configured correctly.",
                settings.consoleBaseUrl() + "/settings/notifications", now);
        String error = dispatcher.sendNow(channel, message, UUID.randomUUID());
        NotificationDelivery recorded = deliveries.recordAttempt(channel, NotificationDelivery.Source.TEST, message,
                error == null, error, now);
        audit(actor, AuditAction.NOTIFICATION_CHANNEL_TESTED, "NOTIFICATION_CHANNEL", channelId, context,
                Map.of("success", String.valueOf(error == null)));
        String detail = error != null ? error
                : channel.type() == ChannelType.EMAIL ? "Test e-mail queued in the outbox" : "Test message delivered";
        return new TestResult(error == null, detail, deliveryView(recorded, Map.of(channel.id(), channel)));
    }

    // --------------------------------------------------------------- rules

    @Override
    @Transactional(readOnly = true)
    public List<RuleView> rules(long organizationId) {
        List<NotificationRule> rules = config.rules(organizationId);
        Map<Long, NotificationChannel> channels = channelMap(organizationId);
        return rules.stream().map(r -> view(r, channels)).toList();
    }

    @Override
    public RuleView createRule(Actor actor, RuleCommand c, RequestContext context) {
        NotificationRule.validate(c.name(), c.channelIds());
        requireRuleFields(c);
        Instant now = clock.instant();
        NotificationRule saved = config.saveRule(new NotificationRule(0, UUID.randomUUID(), actor.organizationId(),
                c.name().trim(), c.eventType().name(), c.minSeverity(), providerCode(c.providerCode()),
                resolveChannels(actor.organizationId(), c.channelIds()), c.enabled(), now, now), now);
        audit(actor, AuditAction.NOTIFICATION_RULE_CHANGED, "NOTIFICATION_RULE", saved.publicId(), context,
                Map.of("operation", "CREATED", "event", saved.eventType()));
        return view(saved, channelMap(actor.organizationId()));
    }

    @Override
    public RuleView updateRule(Actor actor, UUID ruleId, RuleCommand c, RequestContext context) {
        NotificationRule rule = config.rule(actor.organizationId(), ruleId)
                .orElseThrow(() -> new NotFoundException("Notification rule", ruleId.toString()));
        NotificationRule.validate(c.name(), c.channelIds());
        requireRuleFields(c);
        Instant now = clock.instant();
        NotificationRule saved = config.saveRule(new NotificationRule(rule.id(), rule.publicId(), rule.organizationId(),
                c.name().trim(), c.eventType().name(), c.minSeverity(), providerCode(c.providerCode()),
                resolveChannels(actor.organizationId(), c.channelIds()), c.enabled(), rule.createdAt(), now), now);
        audit(actor, AuditAction.NOTIFICATION_RULE_CHANGED, "NOTIFICATION_RULE", ruleId, context,
                Map.of("operation", "UPDATED", "enabled", String.valueOf(c.enabled())));
        return view(saved, channelMap(actor.organizationId()));
    }

    @Override
    public void deleteRule(Actor actor, UUID ruleId, RequestContext context) {
        NotificationRule rule = config.rule(actor.organizationId(), ruleId)
                .orElseThrow(() -> new NotFoundException("Notification rule", ruleId.toString()));
        config.deleteRule(actor.organizationId(), rule.id());
        audit(actor, AuditAction.NOTIFICATION_RULE_CHANGED, "NOTIFICATION_RULE", ruleId, context,
                Map.of("operation", "DELETED"));
    }

    // ---------------------------------------------------------- escalation

    @Override
    @Transactional(readOnly = true)
    public List<PolicyView> escalationPolicies(long organizationId) {
        Map<Long, NotificationChannel> channels = channelMap(organizationId);
        return config.policies(organizationId).stream().map(p -> view(p, channels)).toList();
    }

    @Override
    public PolicyView createPolicy(Actor actor, PolicyCommand c, RequestContext context) {
        Instant now = clock.instant();
        EscalationPolicy saved = config.savePolicy(new EscalationPolicy(0, UUID.randomUUID(), actor.organizationId(),
                requireName(c.name()), requireSeverity(c.minSeverity()), c.enabled(), levels(actor.organizationId(), c),
                now, now), now);
        audit(actor, AuditAction.ESCALATION_POLICY_CHANGED, "ESCALATION_POLICY", saved.publicId(), context,
                Map.of("operation", "CREATED", "levels", String.valueOf(saved.levels().size())));
        return view(saved, channelMap(actor.organizationId()));
    }

    @Override
    public PolicyView updatePolicy(Actor actor, UUID policyId, PolicyCommand c, RequestContext context) {
        EscalationPolicy policy = config.policy(actor.organizationId(), policyId)
                .orElseThrow(() -> new NotFoundException("Escalation policy", policyId.toString()));
        Instant now = clock.instant();
        EscalationPolicy saved = config.savePolicy(new EscalationPolicy(policy.id(), policy.publicId(),
                policy.organizationId(), requireName(c.name()), requireSeverity(c.minSeverity()), c.enabled(),
                levels(actor.organizationId(), c), policy.createdAt(), now), now);
        audit(actor, AuditAction.ESCALATION_POLICY_CHANGED, "ESCALATION_POLICY", policyId, context,
                Map.of("operation", "UPDATED", "levels", String.valueOf(saved.levels().size())));
        return view(saved, channelMap(actor.organizationId()));
    }

    @Override
    public void deletePolicy(Actor actor, UUID policyId, RequestContext context) {
        EscalationPolicy policy = config.policy(actor.organizationId(), policyId)
                .orElseThrow(() -> new NotFoundException("Escalation policy", policyId.toString()));
        config.deletePolicy(actor.organizationId(), policy.id());
        audit(actor, AuditAction.ESCALATION_POLICY_CHANGED, "ESCALATION_POLICY", policyId, context,
                Map.of("operation", "DELETED"));
    }

    // ---------------------------------------------------------- deliveries

    @Override
    @Transactional(readOnly = true)
    public List<DeliveryView> deliveries(long organizationId, NotificationDelivery.Status status, int limit) {
        Map<Long, NotificationChannel> channels = channelMap(organizationId);
        return deliveries.recent(organizationId, status, Math.min(Math.max(limit, 1), 500)).stream()
                .map(d -> deliveryView(d, channels)).toList();
    }

    // ------------------------------------------------------------------------

    private ChannelTarget secureTarget(ChannelType type, ChannelTarget target) {
        if (type.isUrl() && !settings.allowInsecureTargets()
                && !target.target().toLowerCase(Locale.ROOT).startsWith("https://")) {
            throw new DomainException("INVALID_CHANNEL", "Webhook URLs must use https://");
        }
        return target;
    }

    private List<EscalationPolicy.Level> levels(long organizationId, PolicyCommand c) {
        List<EscalationPolicy.Level> levels = new ArrayList<>();
        List<LevelCommand> commands = c.levels() == null ? List.of() : c.levels();
        for (int i = 0; i < commands.size(); i++) {
            LevelCommand level = commands.get(i);
            List<UUID> channels = level.channelIds() == null ? List.of() : level.channelIds();
            levels.add(new EscalationPolicy.Level(i + 1, level.delayMinutes(),
                    channels.isEmpty() ? List.of() : resolveChannels(organizationId, channels)));
        }
        EscalationPolicy.validate(c.name(), levels);
        return levels;
    }

    /** Maps public channel ids to internal ids, rejecting ids of other tenants. */
    private List<Long> resolveChannels(long organizationId, List<UUID> publicIds) {
        Map<UUID, NotificationChannel> byPublicId = config.channels(organizationId).stream()
                .collect(Collectors.toMap(NotificationChannel::publicId, Function.identity()));
        List<Long> ids = new ArrayList<>();
        for (UUID publicId : new LinkedHashSet<>(publicIds)) {
            NotificationChannel channel = byPublicId.get(publicId);
            if (channel == null) {
                throw new NotFoundException("Notification channel", String.valueOf(publicId));
            }
            ids.add(channel.id());
        }
        return ids;
    }

    private void requireRuleFields(RuleCommand c) {
        if (c.eventType() == null) {
            throw new DomainException("INVALID_RULE", "Event type is required");
        }
        requireSeverity(c.minSeverity());
    }

    private static Severity requireSeverity(Severity severity) {
        if (severity == null) {
            throw new DomainException("INVALID_SEVERITY", "Minimum severity is required");
        }
        return severity;
    }

    private static String providerCode(String code) {
        return code == null || code.isBlank() ? null : code.trim().toUpperCase(Locale.ROOT);
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank() || name.trim().length() > 120) {
            throw new DomainException("INVALID_NAME", "A name of at most 120 characters is required");
        }
        return name.trim();
    }

    private void ensureUniqueName(long organizationId, String name, Long exceptId) {
        boolean taken = config.channels(organizationId).stream()
                .anyMatch(ch -> ch.name().equalsIgnoreCase(name) && (exceptId == null || ch.id() != exceptId));
        if (taken) {
            throw new ConflictException("CHANNEL_NAME_TAKEN", "A channel named \"" + name + "\" already exists");
        }
    }

    private NotificationChannel loadChannel(long organizationId, UUID channelId) {
        return config.channel(organizationId, channelId)
                .orElseThrow(() -> new NotFoundException("Notification channel", channelId.toString()));
    }

    private Map<Long, NotificationChannel> channelMap(long organizationId) {
        return config.channels(organizationId).stream()
                .collect(Collectors.toMap(NotificationChannel::id, Function.identity()));
    }

    private static ChannelView view(NotificationChannel c) {
        return new ChannelView(c.publicId(), c.name(), c.type(), c.enabled(), c.targetDisplay(), c.hasSigningSecret(),
                c.createdBy(), c.createdAt(), c.updatedAt());
    }

    private static List<UUID> publicIds(List<Long> ids, Map<Long, NotificationChannel> channels) {
        return ids.stream().map(channels::get).filter(java.util.Objects::nonNull).map(NotificationChannel::publicId)
                .toList();
    }

    private static RuleView view(NotificationRule r, Map<Long, NotificationChannel> channels) {
        return new RuleView(r.publicId(), r.name(),
                com.payops.shared.application.OperationalEvent.Type.valueOf(r.eventType()), r.minSeverity(),
                r.providerCode(), publicIds(r.channelIds(), channels), r.enabled(), r.createdAt(), r.updatedAt());
    }

    private static PolicyView view(EscalationPolicy p, Map<Long, NotificationChannel> channels) {
        return new PolicyView(p.publicId(), p.name(), p.minSeverity(), p.enabled(),
                p.levels().stream().map(l -> new LevelView(l.number(), l.delayMinutes(),
                        publicIds(l.channelIds(), channels))).toList(),
                p.createdAt(), p.updatedAt());
    }

    private static DeliveryView deliveryView(NotificationDelivery d, Map<Long, NotificationChannel> channels) {
        NotificationChannel channel = d.channelId() == null ? null : channels.get(d.channelId());
        return new DeliveryView(d.publicId(), channel == null ? null : channel.publicId(), d.channelName(),
                d.channelType(), d.source(), d.message().eventType(), d.message().severity(),
                d.message().subjectType(), d.message().subjectReference(), d.message().title(), d.status(),
                d.attempts(), d.lastError(), d.status() == NotificationDelivery.Status.PENDING ? d.nextAttemptAt() : null,
                d.createdAt(), d.sentAt());
    }

    private void audit(Actor actor, AuditAction action, String resourceType, UUID resourceId, RequestContext context,
                       Map<String, String> details) {
        auditTrail.record(new AuditEntry(actor.organizationId(), actor.userId(), actor.email(), action, resourceType,
                resourceId.toString(), AuditOutcome.SUCCESS, context.ipAddress(), context.userAgent(), details,
                clock.instant()));
    }
}
