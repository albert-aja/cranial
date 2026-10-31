package io.cranial.platform.decision.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record DecisionFeedbackView(
        @JsonProperty("id")
        UUID id,

        @JsonProperty("recommendation_id")
        UUID recommendationId,

        @JsonProperty("application_id")
        UUID applicationId,

        @JsonProperty("rating")
        String rating,

        @JsonProperty("applied")
        boolean applied,

        @JsonProperty("measured_outcome")
        Map<String, Object> measuredOutcome,

        @JsonProperty("comments")
        String comments,

        @JsonProperty("external_user_ref")
        String externalUserRef,

        @JsonProperty("created_at")
        Instant createdAt
) {
}

