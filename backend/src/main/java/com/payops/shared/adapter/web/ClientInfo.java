package com.payops.shared.adapter.web;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Network metadata about the caller, used for audit and abuse protection.
 * Relies on {@code server.forward-headers-strategy=framework} so that
 * {@link HttpServletRequest#getRemoteAddr()} already honours a trusted proxy.
 */
public record ClientInfo(String ipAddress, String userAgent) {

    private static final int MAX_USER_AGENT = 512;

    public static ClientInfo from(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        if (userAgent != null && userAgent.length() > MAX_USER_AGENT) {
            userAgent = userAgent.substring(0, MAX_USER_AGENT);
        }
        return new ClientInfo(request.getRemoteAddr(), userAgent);
    }
}
