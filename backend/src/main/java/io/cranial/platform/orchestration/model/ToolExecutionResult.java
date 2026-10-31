package io.cranial.platform.orchestration.model;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.util.Map;

public record ToolExecutionResult(
        @JsonAlias({"toolCode", "tool_code"}) String toolCode,
        boolean success,
        Map<String, Object> data,
        @JsonAlias({"errorMessage", "error_message"}) String errorMessage,
        @JsonAlias({"durationMs", "duration_ms"}) long durationMs
) {
    public static ToolExecutionResult success(String toolCode, Map<String, Object> data, long durationMs) {
        return new ToolExecutionResult(toolCode, true, data != null ? data : Map.of(), null, durationMs);
    }

    public static ToolExecutionResult failure(String toolCode, String error, long durationMs) {
        return new ToolExecutionResult(toolCode, false, Map.of(), error, durationMs);
    }
}

