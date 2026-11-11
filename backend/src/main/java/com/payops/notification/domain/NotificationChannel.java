package com.payops.notification.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * A configured destination of one tenant. The secret target is kept apart
 * (encrypted) and loaded only when delivering.
 *
 * @param targetDisplay masked rendition of the target, safe to return from the API
 */
public record NotificationChannel(long id, UUID publicId, long organizationId, String name, ChannelType type,
                                  boolean enabled, String targetDisplay, boolean hasSigningSecret, String createdBy,
                                  Instant createdAt, Instant updatedAt) {
}
