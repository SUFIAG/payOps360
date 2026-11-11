package com.payops.notification.application.service;

/**
 * Plain settings object so the application service stays framework-free.
 *
 * @param redactDeliveredBodies drop message bodies once delivered (they can contain one-time links);
 *                              disabled in dev so the development mailbox can show them
 * @param batchSize             messages handled per scheduler run
 */
public record NotificationSettings(boolean redactDeliveredBodies, int batchSize) {
}
