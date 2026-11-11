package com.payops.payment.adapter.output.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "payment_events")
class PaymentEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "payment_id", nullable = false, updatable = false)
    private Long paymentId;
    @Column(name = "from_status", updatable = false)
    private String fromStatus;
    @Column(name = "to_status", nullable = false, updatable = false)
    private String toStatus;
    @Column(updatable = false)
    private String reason;
    @Column(name = "error_code", updatable = false)
    private String errorCode;
    @Column(updatable = false)
    private String actor;
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;
}
