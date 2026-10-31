package io.cranial.platform.provider.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;

public record SimulationRequest(
        @NotBlank @JsonAlias({"applicationCode", "application_code"}) String applicationCode,
        @NotBlank @JsonAlias({"capabilityCode", "capability_code"}) String capabilityCode,
        @JsonAlias({"taskCode", "task_code"}) String taskCode,
        @JsonAlias({"dataSensitivity", "data_sensitivity"}) String dataSensitivity
) {
    public SimulationRequest {
        if (dataSensitivity == null || dataSensitivity.isBlank()) {
            dataSensitivity = "INTERNAL";
        }
    }
}
