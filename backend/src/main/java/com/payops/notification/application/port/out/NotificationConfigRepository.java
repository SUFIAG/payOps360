package com.payops.notification.application.port.out;

import com.payops.notification.domain.ChannelTarget;
import com.payops.notification.domain.EscalationPolicy;
import com.payops.notification.domain.NotificationChannel;
import com.payops.notification.domain.NotificationRule;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence of channels, rules and escalation policies. Every lookup by
 * public id is scoped to the tenant. Channel targets are passed in clear and
 * encrypted by the adapter.
 */
public interface NotificationConfigRepository {

    // channels
    List<NotificationChannel> channels(long organizationId);

    Optional<NotificationChannel> channel(long organizationId, UUID publicId);

    Map<Long, NotificationChannel> channelsById(long organizationId, Collection<Long> ids);

    /** Decrypted target of a channel; only used to deliver. */
    ChannelTarget target(long channelId);

    NotificationChannel insertChannel(long organizationId, String name, com.payops.notification.domain.ChannelType type,
                                      boolean enabled, ChannelTarget target, String actor, Instant now);

    NotificationChannel updateChannel(NotificationChannel channel, String name, boolean enabled, ChannelTarget target,
                                      Instant now);

    /** Deletes the channel and removes it from every rule and escalation level. */
    void deleteChannel(long organizationId, long channelId);

    // rules
    List<NotificationRule> rules(long organizationId);

    List<NotificationRule> enabledRules(long organizationId, String eventType);

    Optional<NotificationRule> rule(long organizationId, UUID publicId);

    NotificationRule saveRule(NotificationRule rule, Instant now);

    void deleteRule(long organizationId, long ruleId);

    // escalation policies
    List<EscalationPolicy> policies(long organizationId);

    /** Enabled policies of every tenant that has any. */
    List<EscalationPolicy> enabledPolicies();

    Optional<EscalationPolicy> policy(long organizationId, UUID publicId);

    EscalationPolicy savePolicy(EscalationPolicy policy, Instant now);

    void deletePolicy(long organizationId, long policyId);
}
