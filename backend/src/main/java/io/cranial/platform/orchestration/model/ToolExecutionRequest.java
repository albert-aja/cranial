package io.cranial.platform.orchestration.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;

import java.util.Map;

public record ToolExecutionRequest(
        @NotBlank @JsonAlias({"toolCode", "tool_code"}) String toolCode,
        Map<String, Object> parameters
) {
    public ToolExecutionRequest {
        if (parameters == null) {
            parameters = Map.of();
        }
    }
}

