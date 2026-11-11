package com.payops.notification.application.port.out;

import com.payops.notification.domain.ChannelTarget;
import com.payops.notification.domain.ChannelType;
import com.payops.notification.domain.NotificationMessage;

import java.util.UUID;

/**
 * Delivers a message to an HTTP-based channel (Slack, Teams, webhook,
 * PagerDuty). Throws on any failure so the outbox can retry.
 */
public interface ChannelTransport {

    void send(ChannelType type, ChannelTarget target, NotificationMessage message, UUID deliveryId);
}
