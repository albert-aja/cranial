package io.cranial.platform.analytics.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ForecastPointView(
        @JsonProperty("period")
        String period,

        @JsonProperty("predicted_value")
        Double predictedValue,

        @JsonProperty("lower_bound")
        Double lowerBound,

        @JsonProperty("upper_bound")
        Double upperBound,

        @JsonProperty("confidence_level")
        Double confidenceLevel,

        @JsonProperty("method")
        String method,

        @JsonProperty("data_sufficiency_passed")
        boolean dataSufficiencyPassed
) {
}

