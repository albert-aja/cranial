package io.cranial.platform.decision.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OptimizationConstraintView(
        @JsonProperty("name")
        String name,

        @JsonProperty("type")
        String type, // HARD or SOFT

        @JsonProperty("status")
        String status, // SATISFIED or VIOLATED

        @JsonProperty("current_value")
        Double currentValue,

        @JsonProperty("threshold")
        Double threshold,

        @JsonProperty("description")
        String description
) {
}

