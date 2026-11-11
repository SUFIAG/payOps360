package com.payops.shared.application;

/**
 * Output port for {@link OperationalEvent}s. Subscribers are notified once the
 * publishing transaction has committed (or immediately when there is none),
 * so a rolled-back change never produces a notification.
 */
public interface OperationalEventPublisher {

    void publish(OperationalEvent event);
}
