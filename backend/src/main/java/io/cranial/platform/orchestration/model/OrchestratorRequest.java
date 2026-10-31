package io.cranial.platform.orchestration.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;

import java.util.Map;

public record OrchestratorRequest(
        @NotBlank @JsonAlias({"taskCode", "task_code"}) String taskCode,
        @JsonAlias({"capabilityCode", "capability_code"}) String capabilityCode,
        Map<String, Object> inputs,
        @JsonAlias({"modelOverride", "model_override"}) String modelOverride,
        @JsonAlias({"dataSensitivity", "data_sensitivity"}) String dataSensitivity,
        @JsonAlias({"allowTools", "allow_tools"}) Boolean allowTools,
        @JsonAlias({"dryRun", "dry_run"}) Boolean dryRun,
        @JsonAlias({"locale", "language", "lang"}) String locale
) {
    public OrchestratorRequest {
        if (capabilityCode == null || capabilityCode.isBlank()) {
            capabilityCode = "conversational_intelligence";
        }
        if (dataSensitivity == null || dataSensitivity.isBlank()) {
            dataSensitivity = "INTERNAL";
        }
        if (inputs == null) {
            inputs = Map.of();
        }
        if (allowTools == null) {
            allowTools = true;
        }
        if (dryRun == null) {
            dryRun = false;
        }
        if (locale == null || locale.isBlank()) {
            locale = "en";
        }
    }

    public OrchestratorRequest(
            String taskCode,
            String capabilityCode,
            Map<String, Object> inputs,
            String modelOverride,
            String dataSensitivity,
            Boolean allowTools,
            Boolean dryRun
    ) {
        this(taskCode, capabilityCode, inputs, modelOverride, dataSensitivity, allowTools, dryRun, "en");
    }
}
