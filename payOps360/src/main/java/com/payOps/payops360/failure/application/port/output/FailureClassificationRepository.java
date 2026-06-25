package com.payOps.payops360.failure.application.port.output;

import com.payOps.payops360.common.dto.PagedResponse;
import com.payOps.payops360.failure.application.port.input.QueryFailureClassificationsUseCase;
import com.payOps.payops360.failure.domain.model.FailureClassification;

import java.util.Optional;
import java.util.UUID;

/**
 * Output Port: Failure Classification Repository
 */
public interface FailureClassificationRepository {
    FailureClassification save(FailureClassification classification);
    Optional<FailureClassification> findById(UUID id);
    PagedResponse<FailureClassification> findAll(QueryFailureClassificationsUseCase.FailureQueryParams params);
}

