package com.payops.intelligence.application.port.in;

import com.payops.incident.application.port.in.IncidentView;
import com.payops.intelligence.domain.failure.FailureCategory;
import com.payops.shared.application.Actor;
import com.payops.shared.application.RequestContext;

import java.time.Instant;
import java.util.List;

/**
 * AI root-cause analysis for incidents and individual payments.
 */
public interface InvestigationUseCase {

    IncidentView investigateIncident(Actor actor, String incidentReference, RequestContext context);

    PaymentInsight explainPayment(Actor actor, String paymentReference);

    record PaymentInsight(String reference, String status, String failureCode, FailureCategory failureCategory,
                          String failureMeaning, boolean retryRecommended, String retryAdvice, String operatorAction,
                          String customerMessage, String suggestedProvider, String riskSummary,
                          List<String> riskFactors, String narrative, String engine, Instant generatedAt) {
    }
}
