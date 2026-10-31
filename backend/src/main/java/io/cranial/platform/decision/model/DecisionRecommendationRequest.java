package io.cranial.platform.decision.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

import java.util.Map;

public record DecisionRecommendationRequest(
        @NotBlank
        @JsonProperty("plan_code")
        String planCode,

        @JsonProperty("natural_language_goal")
        String naturalLanguageGoal,

        @JsonProperty("runtime_parameters")
        Map<String, Object> runtimeParameters,

        @JsonProperty("simulation_mode")
        Boolean simulationMode,

        @JsonProperty("allow_soft_constraint_violations")
        Boolean allowSoftConstraintViolations,

        @JsonProperty("locale")
        String locale
) {
}

