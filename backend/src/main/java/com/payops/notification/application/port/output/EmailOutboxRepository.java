package com.payops.notification.application.port.output;

import com.payops.notification.domain.EmailMessage;
import com.payops.notification.domain.EmailStatus;
import com.payops.notification.domain.OutboxEmail;

import java.time.Instant;
import java.util.List;

public interface EmailOutboxRepository {

    void enqueue(EmailMessage message, Instant now);

    List<OutboxEmail> findDue(Instant now, int limit);

    /** Marks a message SENT or LOGGED; optionally drops the bodies (they may contain one-time links). */
    void markDelivered(long id, EmailStatus status, Instant at, boolean redactBodies);

    void markAttemptFailed(long id, String error, Instant nextAttemptAt, boolean terminal);

    List<OutboxEmail> recent(int limit);
}
