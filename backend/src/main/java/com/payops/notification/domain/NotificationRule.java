package com.payops.notification.domain;

import com.payops.shared.domain.DomainException;
import com.payops.shared.domain.Severity;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Routes one kind of operational event of at least {@code minSeverity}
 * (optionally only for one provider) to a set of channels.
 *
 * @param eventType    name of a {@code com.payops.shared.application.OperationalEvent.Type}
 * @param providerCode optional provider filter (null = any provider)
 */
public record NotificationRule(long id, UUID publicId, long organizationId, String name, String eventType,
                               Severity minSeverity, String providerCode, List<Long> channelIds, boolean enabled,
                               Instant createdAt, Instant updatedAt) {

    /** The same event key is delivered at most once per rule within this window. */
    public static final Duration QUIET_WINDOW = Duration.ofMinutes(10);
    public static final int MAX_CHANNELS = 20;

    public NotificationRule {
        channelIds = channelIds == null ? List.of() : List.copyOf(channelIds);
    }

    public static void validate(String name, List<?> channels) {
        if (name == null || name.isBlank()) {
            throw new DomainException("INVALID_RULE", "A rule name is required");
        }
        if (channels == null || channels.isEmpty() || channels.size() > MAX_CHANNELS) {
            throw new DomainException("INVALID_RULE", "Select between 1 and " + MAX_CHANNELS + " channels");
        }
    }

    public boolean matches(String type, Severity severity, String eventProviderCode) {
        return enabled && eventType.equals(type) && severity.isAtLeast(minSeverity)
                && (providerCode == null || providerCode.equalsIgnoreCase(eventProviderCode));
    }
}
