package com.payOps.payops360.incident.adapter.output.persistence;

import com.payOps.payops360.common.dto.PagedResponse;
import com.payOps.payops360.incident.adapter.output.persistence.entity.IncidentEntity;
import com.payOps.payops360.incident.adapter.output.persistence.mapper.IncidentPersistenceMapper;
import com.payOps.payops360.incident.adapter.output.persistence.repository.IncidentJpaRepository;
import com.payOps.payops360.incident.application.port.input.QueryIncidentsUseCase;
import com.payOps.payops360.incident.application.port.output.IncidentRepository;
import com.payOps.payops360.incident.domain.model.Incident;
import com.payOps.payops360.incident.domain.model.IncidentSeverity;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * JPA Adapter implementing IncidentRepository port
 */
@Component
@RequiredArgsConstructor
public class IncidentPersistenceAdapter implements IncidentRepository {

    private final IncidentJpaRepository jpaRepository;
    private final IncidentPersistenceMapper mapper;

    @Override
    public Incident save(Incident incident) {
        IncidentEntity entity = mapper.toEntity(incident);
        IncidentEntity saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<Incident> findById(UUID id) {
        return jpaRepository.findById(id)
                .map(mapper::toDomain);
    }

    @Override
    public PagedResponse<Incident> findAll(QueryIncidentsUseCase.IncidentQueryParams params) {
        Pageable pageable = PageRequest.of(params.page(), params.size());

        IncidentSeverity severity = params.severity() != null
                ? IncidentSeverity.valueOf(params.severity())
                : null;

        Page<IncidentEntity> page = jpaRepository.findAllWithFilters(
                params.status(),
                params.providerId(),
                severity,
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

