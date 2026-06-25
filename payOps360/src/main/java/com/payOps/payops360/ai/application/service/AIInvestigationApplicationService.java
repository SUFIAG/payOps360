package com.payOps.payops360.ai.application.service;

import com.payOps.payops360.ai.application.port.input.*;
import com.payOps.payops360.ai.application.port.output.*;
import com.payOps.payops360.ai.domain.model.AIInvestigation;
import com.payOps.payops360.ai.domain.model.InvestigationContext;
import com.payOps.payops360.ai.domain.service.AIInvestigationService;
import com.payOps.payops360.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Application Service implementing AI Investigation Use Cases
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AIInvestigationApplicationService implements
        InvestigateIncidentUseCase,
        QueryAIInvestigationsUseCase {

    private final AIInvestigationRepository investigationRepository;
    private final ContextBuilder contextBuilder;
    private final AIServiceClient aiServiceClient;
    private final AIInvestigationService investigationService;

    @Override
    public AIInvestigation investigate(InvestigateCommand command) {
        log.info("Starting AI investigation for incident: {}", command.incidentId());

        long startTime = System.currentTimeMillis();

        // Build context from incident data
        InvestigationContext context = contextBuilder.buildContextForIncident(command.incidentId());
        log.info("Context built for incident with {} related alerts", context.getRelatedAlertTypes().size());

        // Generate prompt
        String prompt = investigationService.generatePrompt(context);
        log.debug("Generated AI prompt: {}", prompt.substring(0, Math.min(200, prompt.length())));

        // Call AI service
        String aiResponse = aiServiceClient.generateResponse(prompt, command.model());
        log.info("Received AI response ({} characters)", aiResponse.length());

        // Build investigation
        long processingTime = System.currentTimeMillis() - startTime;
        AIInvestigation investigation = investigationService.buildInvestigation(
                command.incidentId(),
                context,
                aiResponse,
                command.model(),
                processingTime
        );

        // Save investigation
        AIInvestigation saved = investigationRepository.save(investigation);

        log.info("AI investigation complete: Confidence={}, RootCause={}, Recommendations={}, Processing={}ms",
                saved.getConfidenceScore(), saved.getRootCause().substring(0, Math.min(50, saved.getRootCause().length())),
                saved.getRecommendations().size(), saved.getProcessingTimeMs());

        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public AIInvestigation getById(UUID id) {
        return investigationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("AI investigation not found: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public AIInvestigation getByIncidentId(UUID incidentId) {
        return investigationRepository.findByIncidentId(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("No AI investigation found for incident: " + incidentId));
    }
}

