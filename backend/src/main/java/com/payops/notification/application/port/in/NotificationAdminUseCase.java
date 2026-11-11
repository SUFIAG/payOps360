package com.payops.notification.application.port.in;

import com.payops.notification.domain.ChannelType;
import com.payops.notification.domain.NotificationDelivery;
import com.payops.shared.application.Actor;
import com.payops.shared.application.OperationalEvent;
import com.payops.shared.application.RequestContext;
import com.payops.shared.domain.Severity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Tenant administration of notification channels, routing rules and
 * escalation policies, plus the delivery log.
 */
public interface NotificationAdminUseCase {

    // ------------------------------------------------------------ channels

    List<ChannelView> channels(long organizationId);

    ChannelView createChannel(Actor actor, ChannelCommand command, RequestContext context);

    /** Blank {@code target} / {@code signingSecret} keep the stored values. */
    ChannelView updateChannel(Actor actor, UUID channelId, ChannelCommand command, RequestContext context);

    void deleteChannel(Actor actor, UUID channelId, RequestContext context);

    /** Sends a test message synchronously and records it in the delivery log. */
    TestResult testChannel(Actor actor, UUID channelId, RequestContext context);

    // --------------------------------------------------------------- rules

    List<RuleView> rules(long organizationId);

    RuleView createRule(Actor actor, RuleCommand command, RequestContext context);

    RuleView updateRule(Actor actor, UUID ruleId, RuleCommand command, RequestContext context);

    void deleteRule(Actor actor, UUID ruleId, RequestContext context);

    // ---------------------------------------------------------- escalation

    List<PolicyView> escalationPolicies(long organizationId);

    PolicyView createPolicy(Actor actor, PolicyCommand command, RequestContext context);

    PolicyView updatePolicy(Actor actor, UUID policyId, PolicyCommand command, RequestContext context);

    void deletePolicy(Actor actor, UUID policyId, RequestContext context);

    // ---------------------------------------------------------- deliveries

    List<DeliveryView> deliveries(long organizationId, NotificationDelivery.Status status, int limit);

    // -------------------------------------------------------------- models

    record ChannelCommand(String name, ChannelType type, boolean enabled, String target, String signingSecret) {
    }

    record ChannelView(UUID id, String name, ChannelType type, boolean enabled, String target,
                       boolean hasSigningSecret, String createdBy, Instant createdAt, Instant updatedAt) {
    }

    record TestResult(boolean success, String detail, DeliveryView delivery) {
    }

    record RuleCommand(String name, OperationalEvent.Type eventType, Severity minSeverity, String providerCode,
                       List<UUID> channelIds, boolean enabled) {
    }

    record RuleView(UUID id, String name, OperationalEvent.Type eventType, Severity minSeverity, String providerCode,
                    List<UUID> channelIds, boolean enabled, Instant createdAt, Instant updatedAt) {
    }

    record LevelCommand(int delayMinutes, List<UUID> channelIds) {
    }

    record PolicyCommand(String name, Severity minSeverity, boolean enabled, List<LevelCommand> levels) {
    }

    record LevelView(int level, int delayMinutes, List<UUID> channelIds) {
    }

    record PolicyView(UUID id, String name, Severity minSeverity, boolean enabled, List<LevelView> levels,
                      Instant createdAt, Instant updatedAt) {
    }

    record DeliveryView(UUID id, UUID channelId, String channelName, ChannelType channelType,
                        NotificationDelivery.Source source, String eventType, Severity severity, String subjectType,
                        String subjectReference, String title, NotificationDelivery.Status status, int attempts,
                        String lastError, Instant nextAttemptAt, Instant createdAt, Instant sentAt) {
    }
}
