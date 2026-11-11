package com.payops.shared.adapter.web;

import java.util.Map;

/**
 * Error payload. {@code correlationId} lets support teams find the matching
 * server log line without exposing internals to the client.
 */
public record ApiError(String code, String message, Map<String, String> fields, String correlationId) {
}
