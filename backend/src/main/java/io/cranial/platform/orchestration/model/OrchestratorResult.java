package io.cranial.platform.orchestration.model;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record OrchestratorResult(
        @JsonAlias({"traceId", "trace_id"}) UUID traceId,
        @JsonAlias({"taskCode", "task_code"}) String taskCode,
        @JsonAlias({"capabilityCode", "capability_code"}) String capabilityCode,
        String model,
        @JsonAlias({"structuredOutput", "structured_output"}) Map<String, Object> structuredOutput,
        @JsonAlias({"textResponse", "text_response"}) String textResponse,
        @JsonAlias({"promptTokens", "prompt_tokens"}) int promptTokens,
        @JsonAlias({"completionTokens", "completion_tokens"}) int completionTokens,
        @JsonAlias({"totalTokens", "total_tokens"}) int totalTokens,
        @JsonAlias({"durationMs", "duration_ms"}) long durationMs,
        @JsonAlias({"toolCallsCount", "tool_calls_count"}) int toolCallsCount,
        List<OrchestrationStep> steps,
        Map<String, Object> metadata
) {
}

