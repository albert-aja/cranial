package io.cranial.platform.orchestration.model;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.util.Map;

public record OrchestrationStep(
        @JsonAlias({"stepIndex", "step_index"}) int stepIndex,
        @JsonAlias({"stepName", "step_name"}) String stepName,
        @JsonAlias({"stepType", "step_type"}) String stepType, // PROMPT_RESOLUTION, MODEL_INFERENCE, TOOL_INVOCATION, SCHEMA_VALIDATION
        @JsonAlias({"durationMs", "duration_ms"}) long durationMs,
        String status, // SUCCESS, FAILURE
        Map<String, Object> details
) {
    public static OrchestrationStep of(int index, String name, String type, long durationMs, String status, Map<String, Object> details) {
        return new OrchestrationStep(index, name, type, durationMs, status, details != null ? details : Map.of());
    }
}

