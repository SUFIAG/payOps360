package com.payOps.payops360.ai.application.port.output;

import com.payOps.payops360.ai.domain.model.InvestigationContext;

import java.util.UUID;

/**
 * Output Port: Context Builder
 * Aggregates data from multiple sources to build investigation context
 */
public interface ContextBuilder {
    InvestigationContext buildContextForIncident(UUID incidentId);
}

