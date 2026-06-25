package com.payOps.payops360.ai.adapter.output.persistence;

import com.payOps.payops360.ai.adapter.output.persistence.entity.AIInvestigationEntity;
import com.payOps.payops360.ai.adapter.output.persistence.mapper.AIInvestigationPersistenceMapper;
import com.payOps.payops360.ai.adapter.output.persistence.repository.AIInvestigationJpaRepository;
import com.payOps.payops360.ai.application.port.output.AIInvestigationRepository;
import com.payOps/payops360.ai.domain.model.AIInvestigation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * JPA Adapter implementing AIInvestigationRepository port
 */
@Component
@RequiredArgsConstructor
public class AIInvestigationPersistenceAdapter implements AIInvestigationRepository {

    private final AIInvestigationJpaRepository jpaRepository;
    private final AIInvestigationPersistenceMapper mapper;

    @Override
    public AIInvestigation save(AIInvestigation investigation) {
        AIInvestigationEntity entity = mapper.toEntity(investigation);
        AIInvestigationEntity saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<AIInvestigation> findById(UUID id) {
        return jpaRepository.findById(id)
                .map(mapper::toDomain);
    }

    @Override
    public Optional<AIInvestigation> findByIncidentId(UUID incidentId) {
        return jpaRepository.findByIncidentId(incidentId)
                .map(mapper::toDomain);
    }
}

