package com.payops.notification.domain;

public enum EmailStatus {
    /** Queued, waiting for (re)delivery. */
    PENDING,
    /** Accepted by the SMTP server. */
    SENT,
    /** Gave up after the maximum number of attempts. */
    FAILED,
    /** No mail server configured: the message was written to the log instead. */
    LOGGED
}
