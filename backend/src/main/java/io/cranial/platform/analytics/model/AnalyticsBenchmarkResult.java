package io.cranial.platform.analytics.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public record AnalyticsBenchmarkResult(
        @JsonProperty("total_cases")
        int totalCases,

        @JsonProperty("passed_cases")
        int passedCases,

        @JsonProperty("pass_rate")
        double passRate,

        @JsonProperty("calculation_accuracy")
        double calculationAccuracy,

        @JsonProperty("anomaly_detection_rate")
        double anomalyDetectionRate,

        @JsonProperty("false_positive_rejection_rate")
        double falsePositiveRejectionRate,

        @JsonProperty("clarification_accuracy")
        double clarificationAccuracy,

        @JsonProperty("duration_ms")
        long durationMs,

        @JsonProperty("cases")
        List<Map<String, Object>> cases
) {
}

