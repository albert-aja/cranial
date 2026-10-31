package io.cranial.platform.decision.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

public record DecisionBenchmarkResult(
        @JsonProperty("benchmark_run_id")
        String benchmarkRunId,

        @JsonProperty("total_scenarios_tested")
        int totalScenariosTested,

        @JsonProperty("passed_scenarios")
        int passedScenarios,

        @JsonProperty("failed_scenarios")
        int failedScenarios,

        @JsonProperty("constraint_satisfaction_rate")
        double constraintSatisfactionRate,

        @JsonProperty("abstention_accuracy_rate")
        double abstentionAccuracyRate,

        @JsonProperty("average_solver_latency_ms")
        double averageSolverLatencyMs,

        @JsonProperty("test_cases")
        List<DecisionBenchmarkTestCase> testCases,

        @JsonProperty("executed_at")
        Instant executedAt
) {
    public record DecisionBenchmarkTestCase(
            @JsonProperty("scenario_name")
            String scenarioName,

            @JsonProperty("archetype")
            String archetype,

            @JsonProperty("passed")
            boolean passed,

            @JsonProperty("constraints_satisfied")
            boolean constraintsSatisfied,

            @JsonProperty("latency_ms")
            long latencyMs,

            @JsonProperty("error_message")
            String errorMessage
    ) {
    }
}

