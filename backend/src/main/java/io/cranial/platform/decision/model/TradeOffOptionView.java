package io.cranial.platform.decision.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record TradeOffOptionView(
        @JsonProperty("strategy")
        String strategy,

        @JsonProperty("quantitative_metric")
        String quantitativeMetric,

        @JsonProperty("score_or_amount")
        Double scoreOrAmount,

        @JsonProperty("pros")
        List<String> pros,

        @JsonProperty("cons")
        List<String> cons,

        @JsonProperty("recommended")
        boolean recommended
) {
}

