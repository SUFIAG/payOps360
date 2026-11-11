package com.payops.identity.domain;

import com.payops.shared.domain.DomainException;

/**
 * Deliberately vague: an unknown, expired, already used or mismatched token
 * all produce the same error.
 */
public class InvalidTokenException extends DomainException {

    public InvalidTokenException() {
        super("INVALID_TOKEN", "This link is invalid or has expired. Please request a new one.");
    }
}
