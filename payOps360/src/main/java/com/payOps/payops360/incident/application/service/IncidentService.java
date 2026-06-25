package com.payOps.payops360.incident.application.service;

import com.payOps.payops360.common.dto.PagedResponse;
import com.payOps.payops360.common.exception.ResourceNotFoundException;
import com.payOps.payops360.incident.application.port.input.*;
import com.payOps.payops360.incident.application.port.output.IncidentRepository;
import com.payOps.payops360.incident.domain.model.Incident;
import com.payOps.payops360.incident.domain.service.IncidentCorrelationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Application Service implementing all Incident Use Cases
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class IncidentService implements
        CreateIncidentUseCase,
        ManageIncidentUseCase,
        QueryIncidentsUseCase {

    private final IncidentRepository incidentRepository;
    private final IncidentCorrelationService correlationService;

    @Override
    public Incident create(CreateIncidentCommand command) {
        log.info("Creating incident from {} correlated alerts", command.alerts().size());

        Incident incident = correlationService.createIncident(
                command.alerts(),
                command.providerId(),
                command.region()
        );

        Incident saved = incidentRepository.save(incident);
        log.info("Incident created: {} - {} ({})", saved.getId(), saved.getTitle(), saved.getSeverity());

        return saved;
    }

    @Override
    public Incident acknowledge(UUID incidentId, String assignedTo) {
        log.info("Acknowledging incident: {} by {}", incidentId, assignedTo);

        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Incident not found: " + incidentId));

        Incident acknowledged = incident.acknowledge(assignedTo);
        return incidentRepository.save(acknowledged);
    }

    @Override
    public Incident resolve(UUID incidentId, String rootCause, String resolution) {
        log.info("Resolving incident: {}", incidentId);

        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Incident not found: " + incidentId));

        Incident resolved = incident.resolve(rootCause, resolution);

        log.info("Incident resolved: {} (duration: {} minutes)", incidentId, resolved.getDurationMinutes());

        return incidentRepository.save(resolved);
    }

    @Override
    public Incident escalate(UUID incidentId) {
        log.info("Escalating incident: {}", incidentId);

        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Incident not found: " + incidentId));

        Incident escalated = incident.escalate();

        log.warn("Incident escalated to {}: {}", escalated.getSeverity(), incidentId);

        return incidentRepository.save(escalated);
    }

    @Override
    @Transactional(readOnly = true)
    public Incident getById(UUID id) {
        return incidentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Incident not found: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<Incident> findAll(IncidentQueryParams params) {
        return incidentRepository.findAll(params);
    }
}

