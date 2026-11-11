package com.payops.intelligence.application.port.output;

import java.util.List;
import java.util.Map;

/**
 * Provider-agnostic large-language-model port. The Claude adapter implements
 * it; when no model is configured {@link #available()} is false and the
 * application services fall back to their deterministic engines.
 */
public interface GenerativeAiPort {

    boolean available();

    String engineName();

    /**
     * Runs a (possibly multi-step) generation. When tools are supplied the
     * adapter loops: model requests a tool, {@code executor} runs it read-only,
     * the result is fed back, until the model produces a final answer.
     */
    Result generate(Request request, ToolExecutor executor);

    enum Role { USER, ASSISTANT }

    record AiMessage(Role role, String content) {
    }

    record AiTool(String name, String description, Map<String, Object> inputSchema) {
    }

    /**
     * @param jsonSchema when set, the final answer must be JSON matching this schema
     */
    record Request(String feature, String system, List<AiMessage> messages, List<AiTool> tools,
                   Map<String, Object> jsonSchema, int maxTokens) {
    }

    record Result(String text, List<String> toolsUsed, long inputTokens, long outputTokens, String engine) {
    }

    /** The model could not produce an answer; callers fall back to deterministic logic. */
    class GenerationException extends RuntimeException {
        public GenerationException(String message) {
            super(message);
        }
    }

    @FunctionalInterface
    interface ToolExecutor {
        String execute(String toolName, Map<String, Object> input);

        static ToolExecutor none() {
            return (name, input) -> "{\"error\":\"no tools available\"}";
        }
    }
}
