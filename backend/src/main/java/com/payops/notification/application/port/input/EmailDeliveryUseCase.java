package com.payops.notification.application.port.input;

/**
 * Drains the outbox. Invoked by the scheduler on exactly one replica.
 */
public interface EmailDeliveryUseCase {

    /** @return number of messages processed (delivered, logged or failed) */
    int deliverDue();
}
