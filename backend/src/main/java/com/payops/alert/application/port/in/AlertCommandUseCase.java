package com.payops.alert.application.port.in;

import com.payops.shared.application.Actor;
import com.payops.shared.application.RequestContext;

public interface AlertCommandUseCase {

    AlertView acknowledge(Actor actor, String reference, RequestContext context);

    AlertView resolve(Actor actor, String reference, String note, RequestContext context);
}
