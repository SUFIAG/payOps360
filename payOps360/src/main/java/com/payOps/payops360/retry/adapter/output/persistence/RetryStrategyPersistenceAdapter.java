package com.payOps.payops360.retry.adapter.output.persistence;

import com.payOps.payops360.common.dto.PagedResponse;
import com.payOps.payops360.retry.adapter.output.persistence.entity.RetryStrategyEntity;
import com.payOps.payops360.retry.adapter.output.persistence.mapper.RetryStrategyPersistenceMapper;
import com.payOps.payops360.retry.adapter.output.persistence.repository.RetryStrategyJpaRepository;
import com.payOps.payops360.retry.application.port.input.QueryRetryStrategiesUseCase;
import com.payOps.payops360.retry.application.port.output.RetryStrategyRepository;
import com.payOps.payops360.retry.domain.model.RetryStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * JPA Adapter implementing RetryStrategyRepository port
 */
@Component
@RequiredArgsConstructor
public class RetryStrategyPersistenceAdapter implements RetryStrategyRepository {

    private final RetryStrategyJpaRepository jpaRepository;
    private final RetryStrategyPersistenceMapper mapper;

    @Override
    public RetryStrategy save(RetryStrategy retryStrategy) {
        RetryStrategyEntity entity = mapper.toEntity(retryStrategy);
        RetryStrategyEntity saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<RetryStrategy> findById(UUID id) {
        return jpaRepository.findById(id)
                .map(mapper::toDomain);
    }

    @Override
    public PagedResponse<RetryStrategy> findAll(QueryRetryStrategiesUseCase.RetryQueryParams params) {
        Pageable pageable = PageRequest.of(params.page(), params.size());

        Page<RetryStrategyEntity> page = jpaRepository.findAllWithFilters(
                params.paymentId(),
                params.status(),
                params.failureCategory(),
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

