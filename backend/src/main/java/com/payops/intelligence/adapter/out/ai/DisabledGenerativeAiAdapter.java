package com.payops.intelligence.adapter.out.ai;

import com.payops.intelligence.application.port.out.GenerativeAiPort;

/**
 * Used when no LLM credentials are configured. Every AI feature then runs
 * on the built-in deterministic PayOps360 engine.
 */
class DisabledGenerativeAiAdapter implements GenerativeAiPort {

    @Override
    public boolean available() {
        return false;
    }

    @Override
    public String engineName() {
        return "payops-insight-engine";
    }

    @Override
    public Result generate(Request request, ToolExecutor executor) {
        throw new GenerationException("Generative AI is not configured");
    }
}
