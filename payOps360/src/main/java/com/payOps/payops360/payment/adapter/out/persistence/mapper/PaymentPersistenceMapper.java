package com.payops.payops360.payment.adapter.out.persistence.mapper;

import com.payops.payops360.payment.adapter.out.persistence.entity.PaymentEntity;
import com.payops.payops360.payment.adapter.out.persistence.entity.PaymentEventEntity;
import com.payops.payops360.payment.domain.model.Payment;
import com.payops.payops360.payment.domain.model.PaymentEvent;
import com.payops.payops360.payment.domain.valueobject.Currency;
import com.payops.payops360.payment.domain.valueobject.Money;
import org.mapstruct.*;

import java.util.List;

/**
 * MapStruct mapper for Payment persistence.
 *
 * Maps between domain models and JPA entities.
 * This is part of the OUTPUT ADAPTER layer.
 *
 * Critical for hexagonal architecture:
 * - Domain models are pure (no JPA)
 * - Entities have JPA annotations
 * - Mapper handles the conversion
 */
@Mapper(componentModel = "spring")
public interface PaymentPersistenceMapper {

    /**
     * Map domain Payment to JPA entity
     */
    @Mapping(target = "amount", source = "amount.amount")
    @Mapping(target = "currency", source = "amount.currency.name")
    @Mapping(target = "events", ignore = true) // Handle separately
    @Mapping(target = "createdAt", ignore = true) // Managed by JPA
    @Mapping(target = "updatedAt", ignore = true) // Managed by JPA
    PaymentEntity toEntity(Payment payment);

    /**
     * Map JPA entity to domain Payment
     */
    @Mapping(target = "amount", source = ".", qualifiedByName = "toMoney")
    @Mapping(target = "events", source = "events")
    Payment toDomain(PaymentEntity entity);

    /**
     * Map domain PaymentEvent to JPA entity
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "payment", ignore = true)
    PaymentEventEntity toEventEntity(PaymentEvent event);

    /**
     * Map JPA event entity to domain
     */
    PaymentEvent toEventDomain(PaymentEventEntity entity);

    /**
     * Map list of event entities to domain
     */
    List<PaymentEvent> toEventDomainList(List<PaymentEventEntity> entities);

    /**
     * Custom mapping: Convert amount + currency to Money
     */
    @Named("toMoney")
    default Money toMoney(PaymentEntity entity) {
        if (entity.getAmount() == null || entity.getCurrency() == null) {
            return null;
        }
        Currency currency = Currency.fromCode(entity.getCurrency());
        return Money.of(entity.getAmount(), currency);
    }

    /**
     * Update entity from domain (for updates)
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "paymentId", ignore = true) // Immutable
    @Mapping(target = "amount", source = "amount.amount")
    @Mapping(target = "currency", source = "amount.currency.name")
    @Mapping(target = "events", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(@MappingTarget PaymentEntity entity, Payment payment);
}

