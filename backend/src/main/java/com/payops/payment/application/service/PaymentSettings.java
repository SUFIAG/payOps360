package com.payops.payment.application.service;

import com.payops.payment.domain.PaymentStatus;

import java.time.Duration;
import java.util.Map;

/**
 * Maximum time a payment may remain in each in-flight state before it is
 * considered stuck.
 */
public record PaymentSettings(Map<PaymentStatus, Duration> stuckThresholds) {
}
