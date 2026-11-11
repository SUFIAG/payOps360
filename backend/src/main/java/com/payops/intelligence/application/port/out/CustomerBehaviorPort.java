package com.payops.intelligence.application.port.out;

import java.math.BigDecimal;
import java.time.Instant;

public interface CustomerBehaviorPort {

    CustomerProfile profile(long organizationId, String customerRef, Instant at);

    record CustomerProfile(BigDecimal averageUsd30d, long payments30d, long paymentsLastHour, long failures24h,
                           long countries24h) {
    }
}
