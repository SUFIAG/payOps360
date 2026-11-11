package com.payops.identity.domain;

import com.payops.shared.domain.NotFoundException;

/**
 * Self-service sign-up is switched off: the endpoint behaves as if it did not exist (404).
 */
public class SelfSignupDisabledException extends NotFoundException {

    public SelfSignupDisabledException() {
        super("Resource", "signup");
    }
}
