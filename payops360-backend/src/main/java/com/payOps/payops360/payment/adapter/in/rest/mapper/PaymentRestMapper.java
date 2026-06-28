package com.payops.payops360.payment.adapter.in.rest.mapper;

import com.payops.payops360.payment.adapter.in.rest.dto.IngestPaymentRequest;
import com.payops.payops360.payment.adapter.in.rest.dto.PaymentResponse;
import com.payops.payops360.payment.adapter.in.rest.dto.UpdatePaymentStatusRequest;
import com.payops.payops360.payment.application.port.in.IngestPaymentUseCase;
import com.payops.payops360.payment.application.port.in.UpdatePaymentStatusUseCase;
import com.payops.payops360.payment.domain.model.Payment;
import com.payops.payops360.payment.domain.valueobject.Currency;
import com.payops.payops360.payment.domain.valueobject.Money;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

/**
 * MapStruct mapper for Payment DTOs.
 *
 * Type-safe mapping between REST DTOs and domain models/commands.
 * This is part of the INPUT ADAPTER layer.
 */
@Mapper(componentModel = "spring")
public interface PaymentRestMapper {

    /**
     * Map REST request to use case command
     */
    @Mapping(target = "amount", source = ".", qualifiedByName = "toMoney")
    IngestPaymentUseCase.IngestPaymentCommand toCommand(IngestPaymentRequest request);

    /**
     * Map domain Payment to REST response
     */
    @Mapping(target = "amount", source = "amount.amount")
    @Mapping(target = "currency", expression = "java(payment.getAmount().getCurrency().name())")
    PaymentResponse toResponse(Payment payment);

    /**
     * Map update request to command
     */
    @Mapping(target = "paymentId", source = "paymentId")
    @Mapping(target = "newStatus", source = "request.newStatus")
    @Mapping(target = "reason", source = "request.reason")
    @Mapping(target = "errorCode", source = "request.errorCode")
    @Mapping(target = "errorMessage", source = "request.errorMessage")
    UpdatePaymentStatusUseCase.UpdateStatusCommand toUpdateCommand(
            String paymentId,
            UpdatePaymentStatusRequest request
    );

    /**
     * Custom mapping: Convert amount + currency string to Money value object
     */
    @Named("toMoney")
    default Money toMoney(IngestPaymentRequest request) {
        Currency currency = Currency.fromCode(request.getCurrency());
        return Money.of(request.getAmount(), currency);
    }
}

