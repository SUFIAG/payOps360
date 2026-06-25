package com.payOps.payops360.failure.application.port.input;

import com.payOps.payops360.common.dto.PagedResponse;
import com.payOps.payops360.failure.domain.model.FailureClassification;

import java.util.UUID;

/**
 * Input Port: Query Failure Classifications Use Case
 */
public interface QueryFailureClassificationsUseCase {
    FailureClassification getById(UUID id);
    PagedResponse<FailureClassification> findAll(FailureQueryParams params);

    record FailureQueryParams(
            UUID paymentId,
            String failureType,
            String category,
            int page,
            int size
    ) {}
}

