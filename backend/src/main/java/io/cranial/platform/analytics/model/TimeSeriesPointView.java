package io.cranial.platform.analytics.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public record TimeSeriesPointView(
        @JsonProperty("period")
        String period,

        @JsonProperty("metrics")
        Map<String, Double> metrics,

        @JsonProperty("rolling_average")
        Double rollingAverage,

        @JsonProperty("is_anomaly")
        boolean isAnomaly,

        @JsonProperty("anomaly_reason")
        String anomalyReason
) {
}

