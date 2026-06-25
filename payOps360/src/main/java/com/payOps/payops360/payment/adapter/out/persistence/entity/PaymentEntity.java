package com.payops.payops360.payment.adapter.out.persistence.entity;

import com.payops.payops360.common.util.BaseEntity;
import com.payops.payops360.payment.domain.model.PaymentMethodType;
import com.payops.payops360.payment.domain.model.PaymentStatus;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Payment JPA Entity - OUTPUT ADAPTER (Persistence)
 *
 * This is the JPA representation of Payment domain model.
 * Part of the hexagonal architecture's ADAPTER layer.
 *
 * Important:
 * - This has JPA annotations (framework-specific)
 * - Domain model has NO JPA annotations (pure)
 * - Mapping happens in the adapter layer
 */
@Entity
@Table(name = "payments", indexes = {
        @Index(name = "idx_payment_payment_id", columnList = "payment_id"),
        @Index(name = "idx_payment_status", columnList = "status"),
        @Index(name = "idx_payment_provider", columnList = "provider_id"),
        @Index(name = "idx_payment_customer", columnList = "customer_id"),
        @Index(name = "idx_payment_created", columnList = "created_at"),
        @Index(name = "idx_payment_stuck", columnList = "is_stuck, status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_id", unique = true, nullable = false)
    private String paymentId;

    @Column(name = "external_transaction_id")
    private String externalTransactionId;

    @Column(name = "merchant_reference")
    private String merchantReference;

    // Financial Information
    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    // Provider Information
    @Column(name = "provider_id", nullable = false)
    private String providerId;

    @Column(name = "provider_name")
    private String providerName;

    @Column(name = "provider_transaction_id")
    private String providerTransactionId;

    // Customer Information
    @Column(name = "customer_id", nullable = false)
    private String customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method_type")
    private PaymentMethodType paymentMethodType;

    @Column(name = "payment_method_last4", length = 4)
    private String paymentMethodLast4;

    // Status & Lifecycle
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status")
    private PaymentStatus previousStatus;

    @Column(name = "status_changed_at")
    private Instant statusChangedAt;

    // Tracking
    @Column(name = "retry_count")
    @Builder.Default
    private int retryCount = 0;

    @Column(name = "is_stuck")
    @Builder.Default
    private boolean isStuck = false;

    @Column(name = "stuck_detected_at")
    private Instant stuckDetectedAt;

    // Metadata (stored as JSONB in PostgreSQL)
    @Type(JsonBinaryType.class)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Type(JsonBinaryType.class)
    @Column(name = "tags", columnDefinition = "jsonb")
    private List<String> tags;

    // Events (One-to-Many relationship)
    @OneToMany(
            mappedBy = "payment",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    @Builder.Default
    private List<PaymentEventEntity> events = new ArrayList<>();

    /**
     * Helper method to add event
     */
    public void addEvent(PaymentEventEntity event) {
        events.add(event);
        event.setPayment(this);
    }
}

