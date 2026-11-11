package com.payops.intelligence.adapter.out.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicException;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.JsonOutputFormat;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.anthropic.models.messages.ToolUseBlock;
import com.fasterxml.jackson.core.type.TypeReference;
import com.payops.intelligence.application.port.out.GenerativeAiPort;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Claude (Anthropic Messages API) implementation of {@link GenerativeAiPort}.
 *
 * <ul>
 *   <li>Manual, bounded tool loop: tools are read-only and tenant-scoped by the caller.</li>
 *   <li>The stable system prompt is cached (prompt caching) to cut latency and cost.</li>
 *   <li>Server-side refusal fallback is enabled so a declined request is retried on the
 *       recommended model instead of failing.</li>
 *   <li>Structured JSON output when the request carries a schema.</li>
 * </ul>
 */
@Slf4j
public class ClaudeGenerativeAiAdapter implements GenerativeAiPort {

    private static final int MAX_TOOL_ROUNDS = 6;
    private static final Set<String> DEEP_REASONING_FEATURES = Set.of("incident-investigation", "daily-briefing");
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final AnthropicClient client;
    private final String model;
    private final long maxTokens;

    public ClaudeGenerativeAiAdapter(String apiKey, String model, long maxTokens, Duration timeout) {
        this.client = AnthropicOkHttpClient.builder()
                .apiKey(apiKey)
                .timeout(timeout)
                .maxRetries(2)
                .build();
        this.model = model;
        this.maxTokens = maxTokens;
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public String engineName() {
        return "claude:" + model;
    }

    @Override
    public Result generate(Request request, ToolExecutor executor) {
        MessageCreateParams.Builder params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(Math.min(request.maxTokens() > 0 ? request.maxTokens() : maxTokens, maxTokens))
                .systemOfTextBlockParams(List.of(TextBlockParam.builder()
                        .text(request.system())
                        .cacheControl(CacheControlEphemeral.builder().build())
                        .build()))
                .outputConfig(outputConfig(request))
                .putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
                .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"));
        request.tools().forEach(tool -> params.addTool(toTool(tool)));
        request.messages().forEach(m -> {
            if (m.role() == Role.USER) {
                params.addUserMessage(m.content());
            } else {
                params.addAssistantMessage(m.content());
            }
        });

        long inputTokens = 0;
        long outputTokens = 0;
        List<String> toolsUsed = new ArrayList<>();
        try {
            for (int round = 0; round <= MAX_TOOL_ROUNDS; round++) {
                Message response = client.messages().create(params.build());
                inputTokens += response.usage().inputTokens();
                outputTokens += response.usage().outputTokens();
                StopReason stop = response.stopReason().orElse(StopReason.END_TURN);

                if (StopReason.REFUSAL.equals(stop)) {
                    throw new GenerationException("The model declined this request");
                }
                List<ToolUseBlock> toolCalls = response.content().stream()
                        .flatMap(block -> block.toolUse().stream())
                        .toList();
                if (!StopReason.TOOL_USE.equals(stop) || toolCalls.isEmpty()) {
                    return new Result(text(response), toolsUsed, inputTokens, outputTokens, engineName());
                }
                if (round == MAX_TOOL_ROUNDS) {
                    throw new GenerationException("Tool loop exceeded " + MAX_TOOL_ROUNDS + " rounds");
                }
                params.addMessage(response);
                List<ContentBlockParam> results = new ArrayList<>();
                for (ToolUseBlock call : toolCalls) {
                    toolsUsed.add(call.name());
                    results.add(ContentBlockParam.ofToolResult(runTool(call, executor)));
                }
                // All results of one turn go back in a single user message.
                params.addUserMessageOfBlockParams(results);
            }
            throw new GenerationException("Generation did not complete");
        } catch (AnthropicException e) {
            log.warn("Claude request failed for feature={}: {}", request.feature(), e.getMessage());
            throw new GenerationException("Generative AI provider error: " + e.getClass().getSimpleName());
        }
    }

    private ToolResultBlockParam runTool(ToolUseBlock call, ToolExecutor executor) {
        try {
            Map<String, Object> input = call._input().convert(MAP_TYPE);
            String output = executor.execute(call.name(), input == null ? Map.of() : input);
            return ToolResultBlockParam.builder().toolUseId(call.id()).content(output).build();
        } catch (RuntimeException e) {
            log.debug("Tool {} failed: {}", call.name(), e.getMessage());
            return ToolResultBlockParam.builder().toolUseId(call.id())
                    .content("{\"error\":\"tool execution failed\"}").isError(true).build();
        }
    }

    private OutputConfig outputConfig(Request request) {
        OutputConfig.Builder config = OutputConfig.builder()
                .effort(DEEP_REASONING_FEATURES.contains(request.feature()) ? OutputConfig.Effort.HIGH : OutputConfig.Effort.MEDIUM);
        if (request.jsonSchema() != null) {
            JsonOutputFormat.Schema.Builder schema = JsonOutputFormat.Schema.builder();
            request.jsonSchema().forEach((k, v) -> schema.putAdditionalProperty(k, JsonValue.from(v)));
            config.format(JsonOutputFormat.builder().schema(schema.build()).build());
        }
        return config.build();
    }

    @SuppressWarnings("unchecked")
    private static Tool toTool(AiTool tool) {
        Map<String, Object> schema = tool.inputSchema();
        Tool.InputSchema.Properties.Builder properties = Tool.InputSchema.Properties.builder();
        ((Map<String, Object>) schema.getOrDefault("properties", Map.of()))
                .forEach((name, definition) -> properties.putAdditionalProperty(name, JsonValue.from(definition)));
        Tool.InputSchema.Builder inputSchema = Tool.InputSchema.builder().properties(properties.build());
        Object required = schema.get("required");
        if (required instanceof List<?> list && !list.isEmpty()) {
            inputSchema.required(list.stream().map(String::valueOf).toList());
        }
        return Tool.builder().name(tool.name()).description(tool.description()).inputSchema(inputSchema.build()).build();
    }

    private static String text(Message response) {
        return response.content().stream()
                .flatMap(block -> block.text().stream())
                .map(textBlock -> textBlock.text())
                .collect(Collectors.joining("\n"))
                .trim();
    }
}
