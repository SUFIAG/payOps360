package com.payOps.payops360.failure.application.port.input;

import com.payOps.payops360.failure.domain.model.FailureClassification;

import java.util.UUID;

/**
 * Input Port: Classify Failure Use Case
 */
public interface ClassifyFailureUseCase {
    FailureClassification classify(ClassifyFailureCommand command);

    record ClassifyFailureCommand(
            UUID paymentId,
            String errorCode,
            String errorMessage,
            String providerId
    ) {}
}

