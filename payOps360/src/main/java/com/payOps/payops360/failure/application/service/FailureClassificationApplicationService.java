package com.payOps.payops360.failure.application.service;

import com.payOps.payops360.common.dto.PagedResponse;
import com.payOps.payops360.common.exception.ResourceNotFoundException;
import com.payOps.payops360.failure.application.port.input.*;
import com.payOps.payops360.failure.application.port.output.FailureClassificationRepository;
import com.payOps.payops360.failure.domain.model.FailureClassification;
import com.payOps.payops360.failure.domain.service.FailureClassificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Application Service implementing all Failure Classification Use Cases
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class FailureClassificationApplicationService implements
        ClassifyFailureUseCase,
        QueryFailureClassificationsUseCase {

    private final FailureClassificationRepository repository;
    private final FailureClassificationService classificationService;

    @Override
    public FailureClassification classify(ClassifyFailureCommand command) {
        log.info("Classifying failure for payment: {}", command.paymentId());

        FailureClassification classification = classificationService.classifyFailure(
                command.paymentId(),
                command.errorCode(),
                command.errorMessage(),
                command.providerId()
        );

        FailureClassification saved = repository.save(classification);

        log.info("Failure classified: {} - {} (Severity: {}, Retryable: {})",
                saved.getId(), saved.getFailureType(), saved.getSeverity(), saved.isRetryable());

        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public FailureClassification getById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Failure classification not found: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<FailureClassification> findAll(FailureQueryParams params) {
        return repository.findAll(params);
    }
}

