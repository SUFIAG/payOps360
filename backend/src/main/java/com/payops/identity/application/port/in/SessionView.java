package com.payops.identity.application.port.in;

import java.time.Instant;
import java.util.UUID;

public record SessionView(UUID id, String ipAddress, String userAgent, Instant createdAt, Instant lastUsedAt,
                          Instant expiresAt, boolean current) {
}
