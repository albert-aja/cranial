package io.cranial.platform.decision.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public record ScenarioComparisonView(
        @JsonProperty("baseline")
        Map<String, Object> baseline,

        @JsonProperty("recommended")
        Map<String, Object> recommended,

        @JsonProperty("delta_metrics")
        Map<String, Object> deltaMetrics,

        @JsonProperty("summary")
        String summary
) {
}

