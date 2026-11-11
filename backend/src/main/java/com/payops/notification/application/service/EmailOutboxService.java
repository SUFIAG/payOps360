package com.payops.notification.application.service;

import com.payops.notification.application.port.in.EmailDeliveryUseCase;
import com.payops.notification.application.port.in.EmailSender;
import com.payops.notification.application.port.in.MailboxQueryUseCase;
import com.payops.notification.application.port.out.EmailOutboxRepository;
import com.payops.notification.application.port.out.MailTransport;
import com.payops.notification.domain.EmailMessage;
import com.payops.notification.domain.EmailStatus;
import com.payops.notification.domain.OutboxEmail;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Transactional outbox for e-mail. {@link #send} joins the caller's
 * transaction, so a message exists only if the business change committed.
 * Delivery runs outside any transaction: each message's outcome is persisted
 * on its own, failures are retried with exponential back-off and give up
 * after {@link OutboxEmail#MAX_ATTEMPTS}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailOutboxService implements EmailSender, EmailDeliveryUseCase, MailboxQueryUseCase {

    private static final int MAX_ERROR_LENGTH = 1000;

    private final EmailOutboxRepository outbox;
    private final MailTransport transport;
    private final NotificationSettings settings;
    private final Clock clock;

    @Override
    @Transactional
    public void send(EmailMessage message) {
        outbox.enqueue(message, clock.instant());
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public int deliverDue() {
        List<OutboxEmail> due = outbox.findDue(clock.instant(), settings.batchSize());
        for (OutboxEmail email : due) {
            deliver(email);
        }
        return due.size();
    }

    @Override
    @Transactional(readOnly = true)
    public List<OutboxEmail> recent(int limit) {
        return outbox.recent(Math.min(Math.max(limit, 1), 200));
    }

    private void deliver(OutboxEmail email) {
        Instant now = clock.instant();
        try {
            EmailStatus outcome = transport.deliver(email);
            outbox.markDelivered(email.id(), outcome, now, settings.redactDeliveredBodies());
        } catch (RuntimeException ex) {
            int attempts = email.attempts() + 1;
            boolean terminal = attempts >= OutboxEmail.MAX_ATTEMPTS;
            outbox.markAttemptFailed(email.id(), truncate(ex.getClass().getSimpleName() + ": " + ex.getMessage()),
                    now.plus(OutboxEmail.backoffAfter(attempts)), terminal);
            // Never log the body: it can contain one-time links.
            log.warn("E-mail delivery failed id={} category={} attempt={}/{}{}", email.publicId(), email.category(),
                    attempts, OutboxEmail.MAX_ATTEMPTS, terminal ? " (giving up)" : "");
        }
    }

    private static String truncate(String value) {
        return value.length() <= MAX_ERROR_LENGTH ? value : value.substring(0, MAX_ERROR_LENGTH);
    }
}
