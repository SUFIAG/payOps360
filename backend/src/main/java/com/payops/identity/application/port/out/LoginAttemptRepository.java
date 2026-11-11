package com.payops.identity.application.port.out;

import java.time.Instant;

public interface LoginAttemptRepository {

    void record(String email, String ipAddress, String userAgent, boolean success, String reason, Instant at);

    long countFailuresFromIp(String ipAddress, Instant since);
}
