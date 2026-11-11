package com.payops.incident.domain;

import com.payops.shared.domain.DomainException;
import com.payops.shared.domain.Severity;

import java.time.Duration;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Per-severity response targets of one tenant: time to acknowledge and time
 * to resolve. Severities without an explicit target use the defaults.
 */
public final class SlaPolicy {

    public record Target(int ackMinutes, int resolveMinutes) {

        public Target {
            if (ackMinutes < 1 || ackMinutes > 10_080) {
                throw new DomainException("INVALID_SLA", "Acknowledge target must be between 1 minute and 7 days");
            }
            if (resolveMinutes < 1 || resolveMinutes > 43_200) {
                throw new DomainException("INVALID_SLA", "Resolve target must be between 1 minute and 30 days");
            }
            if (resolveMinutes < ackMinutes) {
                throw new DomainException("INVALID_SLA", "Resolve target must not be shorter than the acknowledge target");
            }
        }

        public Duration ack() {
            return Duration.ofMinutes(ackMinutes);
        }

        public Duration resolve() {
            return Duration.ofMinutes(resolveMinutes);
        }
    }

    private static final Map<Severity, Target> DEFAULTS = Map.of(
            Severity.CRITICAL, new Target(5, 60),
            Severity.HIGH, new Target(15, 240),
            Severity.MEDIUM, new Target(60, 1_440),
            Severity.LOW, new Target(240, 4_320));

    private final Map<Severity, Target> targets;

    private SlaPolicy(Map<Severity, Target> targets) {
        this.targets = targets;
    }

    public static SlaPolicy defaults() {
        return of(Map.of());
    }

    /** Overrides on top of the defaults. */
    public static SlaPolicy of(Map<Severity, Target> overrides) {
        Map<Severity, Target> merged = new EnumMap<>(DEFAULTS);
        merged.putAll(overrides);
        return new SlaPolicy(Collections.unmodifiableMap(merged));
    }

    public static Target defaultTarget(Severity severity) {
        return DEFAULTS.get(severity);
    }

    public Target targetFor(Severity severity) {
        return targets.get(severity);
    }

    public Map<Severity, Target> targets() {
        return targets;
    }
}
