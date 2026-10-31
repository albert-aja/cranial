package io.cranial.platform.decision.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

import java.util.Map;

public record RecommendationFeedbackRequest(
        @NotBlank
        @JsonProperty("rating")
        String rating, // HELPFUL, UNHELPFUL, OVERRIDDEN, REJECTED

        @JsonProperty("applied")
        Boolean applied,

        @JsonProperty("measured_outcome")
        Map<String, Object> measuredOutcome,

        @JsonProperty("comments")
        String comments
) {
}

