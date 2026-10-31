package io.cranial.platform.analytics.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record DeterministicProvenance(
        @JsonProperty("formula")
        String formula,

        @JsonProperty("engine")
        String engine,

        @JsonProperty("operands")
        List<Object> operands,

        @JsonProperty("parameters")
        Map<String, Object> parameters,

        @JsonProperty("calculated_at")
        Instant calculatedAt
) {
}

