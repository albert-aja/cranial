package io.cranial.platform.analytics.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record KpiCardView(
        @JsonProperty("name")
        String name,

        @JsonProperty("label")
        String label,

        @JsonProperty("formatted_value")
        String formattedValue,

        @JsonProperty("numeric_value")
        Double numericValue,

        @JsonProperty("unit")
        String unit,

        @JsonProperty("variance_percent")
        Double variancePercent,

        @JsonProperty("trend_direction")
        String trendDirection,

        @JsonProperty("provenance_formula")
        String provenanceFormula
) {
}

