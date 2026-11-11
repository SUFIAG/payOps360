package com.payops.shared.domain;

public class NotFoundException extends DomainException {

    public NotFoundException(String resource, Object id) {
        super("NOT_FOUND", resource + " '" + id + "' was not found");
    }
}
