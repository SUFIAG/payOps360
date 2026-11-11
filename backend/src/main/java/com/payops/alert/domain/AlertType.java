package com.payops.alert.domain;

public enum AlertType {
    PROVIDER_DEGRADED,
    PROVIDER_DOWN,
    FAILURE_RATE_SPIKE,
    LATENCY_SPIKE,
    VOLUME_DROP,
    VOLUME_SURGE,
    STUCK_PAYMENTS,
    HIGH_RISK_PAYMENT,
    FAILURE_CLUSTER
}
