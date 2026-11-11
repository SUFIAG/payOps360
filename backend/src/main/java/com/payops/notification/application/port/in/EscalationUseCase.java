package com.payops.notification.application.port.in;

/**
 * Escalation engine, invoked every minute by the scheduler on exactly one replica.
 */
public interface EscalationUseCase {

    /**
     * Notifies the next level of the applicable escalation policy for every
     * incident / critical alert still unacknowledged past that level's delay.
     *
     * @return number of escalation steps taken
     */
    int escalateDue();
}
