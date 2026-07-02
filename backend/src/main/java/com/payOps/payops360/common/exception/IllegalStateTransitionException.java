package com.payops.payops360.common.exception;

/**
 * Exception thrown when a state transition is not allowed.
 */
public class IllegalStateTransitionException extends BusinessException {

    public IllegalStateTransitionException(String fromState, String toState, String reason) {
        super(
            "ILLEGAL_STATE_TRANSITION",
            String.format("Cannot transition from %s to %s: %s", fromState, toState, reason)
        );
    }
}

