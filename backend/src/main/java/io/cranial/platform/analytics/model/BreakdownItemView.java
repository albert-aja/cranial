package io.cranial.platform.analytics.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record BreakdownItemView(
        @JsonProperty("category")
        String category,

        @JsonProperty("amount")
        Double amount,

        @JsonProperty("percentage")
        Double percentage,

        @JsonProperty("budget_target")
        Double budgetTarget,

        @JsonProperty("variance_amount")
        Double varianceAmount,

        @JsonProperty("status_label")
        String statusLabel
) {
}

