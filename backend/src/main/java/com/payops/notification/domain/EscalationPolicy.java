package com.payops.notification.domain;

import com.payops.shared.domain.DomainException;
import com.payops.shared.domain.Severity;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Ordered escalation levels for unacknowledged incidents (of at least
 * {@code minSeverity}) and unacknowledged critical alerts. Level 1 is
 * notified {@code delayMinutes} after the subject opened; each further level
 * {@code delayMinutes} after the previous level, while nobody acknowledges.
 */
public record EscalationPolicy(long id, UUID publicId, long organizationId, String name, Severity minSeverity,
                               boolean enabled, List<Level> levels, Instant createdAt, Instant updatedAt) {

    public static final int MAX_LEVELS = 10;

    public record Level(int number, int delayMinutes, List<Long> channelIds) {

        public Level {
            channelIds = channelIds == null ? List.of() : List.copyOf(channelIds);
        }
    }

    public EscalationPolicy {
        levels = levels == null ? List.of()
                : levels.stream().sorted(Comparator.comparingInt(Level::number)).toList();
    }

    public static void validate(String name, List<Level> levels) {
        if (name == null || name.isBlank()) {
            throw new DomainException("INVALID_POLICY", "A policy name is required");
        }
        if (levels == null || levels.isEmpty() || levels.size() > MAX_LEVELS) {
            throw new DomainException("INVALID_POLICY", "Define between 1 and " + MAX_LEVELS + " levels");
        }
        for (Level level : levels) {
            if (level.delayMinutes() < 0 || level.delayMinutes() > 1440) {
                throw new DomainException("INVALID_POLICY", "Level delays must be between 0 and 1440 minutes");
            }
            if (level.channelIds().isEmpty() || level.channelIds().size() > NotificationRule.MAX_CHANNELS) {
                throw new DomainException("INVALID_POLICY", "Every level needs at least one channel");
            }
        }
    }

    public boolean appliesTo(Severity severity) {
        return enabled && severity.isAtLeast(minSeverity);
    }

    /**
     * The next level to notify, if it is due.
     *
     * @param notifiedLevel   highest level notified so far (0 = none)
     * @param lastEscalatedAt when that level was notified (or when the subject opened)
     */
    public java.util.Optional<Level> dueLevel(int notifiedLevel, Instant lastEscalatedAt, Instant now) {
        if (notifiedLevel >= levels.size()) {
            return java.util.Optional.empty();
        }
        Level next = levels.get(notifiedLevel);
        Instant due = lastEscalatedAt.plus(Duration.ofMinutes(next.delayMinutes()));
        return now.isBefore(due) ? java.util.Optional.empty() : java.util.Optional.of(next);
    }
}
