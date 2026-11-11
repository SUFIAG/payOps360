package com.payops.reconciliation.application.port.input;

import com.payops.reconciliation.domain.ResolutionReason;
import com.payops.shared.application.Actor;
import com.payops.shared.application.RequestContext;

/**
 * Exception queue workflow. Every action is audited.
 */
public interface ExceptionCommandUseCase {

    /** Assigns an owner; an OPEN exception moves to INVESTIGATING. */
    ExceptionView assign(Actor actor, String reference, String assignee, RequestContext context);

    ExceptionView resolve(Actor actor, String reference, ResolutionReason reason, String note, RequestContext context);

    /** Links the exception's settlement line to a ledger payment and resolves it. */
    ExceptionView manualMatch(Actor actor, String reference, String paymentReference, String note,
                              RequestContext context);

    ExceptionView writeOff(Actor actor, String reference, String note, RequestContext context);
}
