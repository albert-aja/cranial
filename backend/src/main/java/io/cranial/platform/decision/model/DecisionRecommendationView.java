package io.cranial.platform.decision.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record DecisionRecommendationView(
        @JsonProperty("id")
        UUID id,

        @JsonProperty("plan_id")
        UUID planId,

        @JsonProperty("plan_code")
        String planCode,

        @JsonProperty("application_id")
        UUID applicationId,

        @JsonProperty("tenant_id")
        UUID tenantId,

        @JsonProperty("recommendation_code")
        String recommendationCode,

        @JsonProperty("title")
        String title,

        @JsonProperty("summary")
        String summary,

        @JsonProperty("confidence_score")
        double confidenceScore,

        @JsonProperty("abstained")
        boolean abstained,

        @JsonProperty("abstention_reason")
        String abstentionReason,

        @JsonProperty("simulation_mode")
        boolean simulationMode,

        @JsonProperty("status")
        String status,

        @JsonProperty("action_type")
        String actionType,

        @JsonProperty("evidence_rationale")
        Map<String, Object> evidenceRationale,

        @JsonProperty("trade_offs")
        List<TradeOffOptionView> tradeOffs,

        @JsonProperty("scenario_diff")
        ScenarioComparisonView scenarioDiff,

        @JsonProperty("constraints_validated")
        List<OptimizationConstraintView> constraintsValidated,

        @JsonProperty("reviewed_by")
        String reviewedBy,

        @JsonProperty("reviewed_at")
        Instant reviewedAt,

        @JsonProperty("review_notes")
        String reviewNotes,

        @JsonProperty("source_freshness_at")
        Instant sourceFreshnessAt,

        @JsonProperty("created_at")
        Instant createdAt
) {
}

