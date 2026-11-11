package com.payops.shared.application;

/**
 * Output port for pushing real-time events (new payments, alerts, incidents)
 * to connected operator consoles of one tenant.
 */
public interface LiveEventPublisher {

    void publish(long organizationId, String type, Object payload);
}
