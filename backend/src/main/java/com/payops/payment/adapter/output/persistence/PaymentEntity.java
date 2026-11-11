package com.payops.payment.adapter.output.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "payments")
class PaymentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "organization_id", nullable = false, updatable = false)
    private Long organizationId;
    @Column(nullable = false, updatable = false)
    private String reference;
    @Column(name = "idempotency_key", updatable = false)
    private String idempotencyKey;
    @Column(name = "merchant_reference", updatable = false)
    private String merchantReference;
    @Column(nullable = false, updatable = false)
    private BigDecimal amount;
    @Column(nullable = false, updatable = false)
    private String currency;
    @Column(name = "provider_id", nullable = false, updatable = false)
    private Long providerId;
    @Column(name = "customer_ref", nullable = false, updatable = false)
    private String customerRef;
    @Column(nullable = false, updatable = false)
    private String method;
    @Column(name = "card_last4", updatable = false)
    private String cardLast4;
    @Column(updatable = false)
    private String country;
    private String status;
    @Column(name = "previous_status")
    private String previousStatus;
    @Column(name = "status_changed_at")
    private Instant statusChangedAt;
    @Column(name = "failure_code")
    private String failureCode;
    @Column(name = "failure_message")
    private String failureMessage;
    @Column(name = "latency_ms")
    private Integer latencyMs;
    @Column(name = "retry_count")
    private int retryCount;
    private boolean stuck;
    @Column(name = "risk_score")
    private int riskScore;
    @Column(name = "risk_level")
    private String riskLevel;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "risk_factors", columnDefinition = "jsonb")
    private List<String> riskFactors;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private long version;
}
