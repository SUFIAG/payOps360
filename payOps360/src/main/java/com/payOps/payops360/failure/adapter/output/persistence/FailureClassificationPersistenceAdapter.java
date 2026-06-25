package com.payOps.payops360.failure.adapter.output.persistence;

import com.payOps.payops360.common.dto.PagedResponse;
import com.payOps.payops360.failure.adapter.output.persistence.entity.FailureClassificationEntity;
import com.payOps.payops360.failure.adapter.output.persistence.mapper.FailureClassificationPersistenceMapper;
import com.payOps/payops360.failure.adapter.output.persistence.repository.FailureClassificationJpaRepository;
import com.payOps.payops360.failure.application.port.input.QueryFailureClassificationsUseCase;
import com.payOps/payops360.failure.application.port.output.FailureClassificationRepository;
import com.payOps/payops360.failure.domain.model.FailureClassification;
import com.payOps/payops360.failure.domain.model.FailureType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * JPA Adapter implementing FailureClassificationRepository port
 */
@Component
@RequiredArgsConstructor
public class FailureClassificationPersistenceAdapter implements FailureClassificationRepository {

    private final FailureClassificationJpaRepository jpaRepository;
    private final Failure ClassificationPersistenceMapper mapper;

    @Override
    public FailureClassification save(FailureClassification classification) {
        FailureClassificationEntity entity = mapper.toEntity(classification);
        FailureClassificationEntity saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<FailureClassification> findById(UUID id) {
        return jpaRepository.findById(id)
                .map(mapper::toDomain);
    }

    @Override
    public PagedResponse<FailureClassification> findAll(QueryFailureClassificationsUseCase.FailureQueryParams params) {
        Pageable pageable = PageRequest.of(params.page(), params.size());

        FailureType failureType = params.failureType() != null
                ? FailureType.valueOf(params.failureType())
                : null;

        Page<FailureClassificationEntity> page = jpaRepository.findAllWithFilters(
                params.paymentId(),
                failureType,
                params.category(),
                pageable
        );

        return new PagedResponse<>(
                page.getContent().stream().map(mapper::toDomain).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}

