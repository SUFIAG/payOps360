package com.payOps.payops360.ai.application.port.output;

/**
 * Output Port: AI Service Client
 * Abstract interface for calling AI services (OpenAI, Claude, etc.)
 */
public interface AIServiceClient {
    String generateResponse(String prompt, String model);
}

