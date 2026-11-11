package com.payops.incident.domain;

import java.time.Instant;

/**
 * SLA deadlines of an incident and when (if ever) each was breached.
 */
public record SlaClock(Instant ackDueAt, Instant resolveDueAt, Instant ackBreachedAt, Instant resolveBreachedAt) {

    public enum Kind { ACKNOWLEDGE, RESOLVE }

    public static SlaClock none() {
        return new SlaClock(null, null, null, null);
    }

    public boolean ackBreached() {
        return ackBreachedAt != null;
    }

    public boolean resolveBreached() {
        return resolveBreachedAt != null;
    }

    public boolean anyBreached() {
        return ackBreached() || resolveBreached();
    }
}
