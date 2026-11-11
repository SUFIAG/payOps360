package com.payops.notification.application.port.out;

import com.payops.notification.domain.EmailStatus;
import com.payops.notification.domain.OutboxEmail;

/**
 * Hands a message to the outside world (SMTP, or the log when no mail server
 * is configured). Throws on a delivery failure so the outbox can retry.
 */
public interface MailTransport {

    /** @return {@link EmailStatus#SENT} or {@link EmailStatus#LOGGED} */
    EmailStatus deliver(OutboxEmail email);
}
