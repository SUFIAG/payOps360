package com.payops.notification.application.port.in;

import com.payops.notification.domain.OutboxEmail;

import java.util.List;

/**
 * Read access to recent outbox messages. Backs the development mailbox only.
 */
public interface MailboxQueryUseCase {

    List<OutboxEmail> recent(int limit);
}
