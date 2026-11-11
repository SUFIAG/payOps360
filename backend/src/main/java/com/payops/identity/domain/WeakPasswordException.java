package com.payops.identity.domain;

import com.payops.shared.domain.DomainException;

import java.util.List;

public class WeakPasswordException extends DomainException {

    private final List<String> violations;

    public WeakPasswordException(List<String> violations) {
        super("WEAK_PASSWORD", "Password does not meet policy: " + String.join("; ", violations));
        this.violations = List.copyOf(violations);
    }

    public List<String> violations() {
        return violations;
    }
}
