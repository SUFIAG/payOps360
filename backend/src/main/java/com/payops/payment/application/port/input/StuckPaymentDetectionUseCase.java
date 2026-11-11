package com.payops.payment.application.port.input;

public interface StuckPaymentDetectionUseCase {

    /** Flags in-flight payments that exceeded their state's time budget. Returns how many were newly flagged. */
    int detectStuckPayments();
}
