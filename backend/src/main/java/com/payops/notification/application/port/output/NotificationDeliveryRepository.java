package com.payops.notification.application.port.output;

import com.payops.notification.domain.NotificationChannel;
import com.payops.notification.domain.NotificationDelivery;
import com.payops.notification.domain.NotificationMessage;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface NotificationDeliveryRepository {

    NotificationDelivery enqueue(NotificationChannel channel, Long ruleId, NotificationDelivery.Source source,
                                 String eventKey, NotificationMessage message, Instant now);

    /** Records a delivery that was already attempted synchronously (channel tests). */
    NotificationDelivery recordAttempt(NotificationChannel channel, NotificationDelivery.Source source,
                                       NotificationMessage message, boolean success, String error, Instant now);

    List<NotificationDelivery> findDue(Instant now, int limit);

    void markSent(long id, Instant at);

    void markAttemptFailed(long id, String error, Instant nextAttemptAt, boolean terminal);

    List<NotificationDelivery> recent(long organizationId, NotificationDelivery.Status status, int limit);

    Optional<NotificationDelivery> find(long id);

    /**
     * Atomically claims the (rule, event key) pair for the quiet window.
     *
     * @return true when the event should be delivered, false when it was delivered within the window
     */
    boolean claimDedup(long ruleId, String eventKey, Instant now, Duration window);

    /** Drops de-duplication markers older than {@code before}. */
    int purgeDedup(Instant before);
}
