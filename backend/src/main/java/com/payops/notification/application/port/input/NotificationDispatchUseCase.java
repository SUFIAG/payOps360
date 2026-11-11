package com.payops.notification.application.port.input;

import com.payops.shared.application.OperationalEvent;

/**
 * Turns operational events into channel deliveries and drains the delivery
 * outbox. {@link #deliverDue()} is invoked by the scheduler on exactly one replica.
 */
public interface NotificationDispatchUseCase {

    /**
     * Applies the tenant's routing rules (with per-rule de-duplication) and queues deliveries.
     *
     * @return number of deliveries queued
     */
    int route(OperationalEvent event);

    /** @return number of deliveries processed (sent or failed) */
    int deliverDue();
}
