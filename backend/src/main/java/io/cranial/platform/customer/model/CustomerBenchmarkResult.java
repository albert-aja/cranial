package io.cranial.platform.customer.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record CustomerBenchmarkResult(
        @JsonProperty("total_evaluated") int totalEvaluated,
        @JsonProperty("passed_cases") int passedCases,
        @JsonProperty("failed_cases") int failedCases,
        @JsonProperty("sentiment_accuracy_percent") double sentimentAccuracyPercent,
        @JsonProperty("classification_f1_score") double classificationF1Score,
        @JsonProperty("promise_guard_safety_rate_percent") double promiseGuardSafetyRatePercent,
        @JsonProperty("escalation_precision_percent") double escalationPrecisionPercent,
        @JsonProperty("slang_cases_evaluated") int slangCasesEvaluated,
        @JsonProperty("slang_normalization_accuracy_percent") double slangNormalizationAccuracyPercent,
        @JsonProperty("suite_breakdowns") Map<String, Integer> suiteBreakdowns,
        @JsonProperty("evaluation_details") List<Map<String, Object>> evaluationDetails,
        @JsonProperty("evaluated_at") Instant evaluatedAt
) {}
