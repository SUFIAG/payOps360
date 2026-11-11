package com.payops.shared.application;

/**
 * Network context of the caller, captured by inbound adapters for audit
 * and abuse detection.
 */
public record RequestContext(String ipAddress, String userAgent) {

    public static RequestContext internal() {
        return new RequestContext("internal", "payops360-scheduler");
    }
}
