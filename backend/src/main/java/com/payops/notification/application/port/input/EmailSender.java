package com.payops.notification.application.port.input;

import com.payops.notification.domain.EmailMessage;

/**
 * Input port every module uses to send transactional e-mail. The message is
 * written to the outbox inside the caller's transaction (it is only delivered
 * if the business change commits) and sent asynchronously.
 */
public interface EmailSender {

    void send(EmailMessage message);
}
