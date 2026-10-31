package io.cranial.platform.analytics.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AnomalyItemView(
        @JsonProperty("id")
        UUID id,

        @JsonProperty("dataset_id")
        UUID datasetId,

        @JsonProperty("dataset_code")
        String datasetCode,

        @JsonProperty("metric_name")
        String metricName,

        @JsonProperty("period_timestamp")
        Instant periodTimestamp,

        @JsonProperty("actual_value")
        BigDecimal actualValue,

        @JsonProperty("expected_baseline")
        BigDecimal expectedBaseline,

        @JsonProperty("deviation_score")
        BigDecimal deviationScore,

        @JsonProperty("direction")
        String direction,

        @JsonProperty("severity")
        String severity,

        @JsonProperty("algorithm")
        String algorithm,

        @JsonProperty("status")
        String status,

        @JsonProperty("review_notes")
        String reviewNotes,

        @JsonProperty("reviewed_by")
        String reviewedBy,

        @JsonProperty("reviewed_at")
        Instant reviewedAt,

        @JsonProperty("details")
        Map<String, Object> details,

        @JsonProperty("created_at")
        Instant createdAt
) {
}

