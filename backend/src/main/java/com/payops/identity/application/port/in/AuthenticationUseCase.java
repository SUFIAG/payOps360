package com.payops.identity.application.port.in;

import com.payops.shared.application.RequestContext;

public interface AuthenticationUseCase {

    AuthOutcome login(String email, String password, RequestContext context);

    AuthOutcome completeMfa(String challengeToken, String code, RequestContext context);

    MfaEnrollment beginEnrollment(String challengeToken);

    AuthOutcome completeEnrollment(String challengeToken, String code, RequestContext context);

    AuthOutcome refresh(String refreshToken, RequestContext context);

    void logout(String refreshToken, RequestContext context);
}
