package com.payops.incident.application.port.in;

import com.payops.incident.domain.AiAnalysis;
import com.payops.shared.application.Actor;
import com.payops.shared.application.RequestContext;
import com.payops.shared.domain.Severity;

public interface IncidentCommandUseCase {

    IncidentView declare(Actor actor, String title, String description, Severity severity, String providerCode,
                         RequestContext context);

    IncidentView acknowledge(Actor actor, String reference, RequestContext context);

    IncidentView investigate(Actor actor, String reference, RequestContext context);

    IncidentView escalate(Actor actor, String reference, Severity severity, RequestContext context);

    IncidentView resolve(Actor actor, String reference, String note, RequestContext context);

    IncidentView addNote(Actor actor, String reference, String note);

    IncidentView recordAnalysis(long organizationId, String reference, AiAnalysis analysis);
}
