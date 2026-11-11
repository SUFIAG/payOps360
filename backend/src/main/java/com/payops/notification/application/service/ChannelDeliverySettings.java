package com.payops.notification.application.service;

/**
 * Plain settings object so the application services stay framework-free.
 *
 * @param batchSize            deliveries handled per scheduler run
 * @param allowInsecureTargets accept plain-http / private-network webhook targets (development only)
 * @param consoleBaseUrl       public URL of the web console, used for deep links in messages
 */
public record ChannelDeliverySettings(int batchSize, boolean allowInsecureTargets, String consoleBaseUrl) {
}
