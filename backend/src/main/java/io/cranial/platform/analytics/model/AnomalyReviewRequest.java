package io.cranial.platform.analytics.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record AnomalyReviewRequest(
        @NotBlank(message = "status is required")
        @JsonProperty("status")
        String status, // VERIFIED_ANOMALY, FALSE_POSITIVE, RESOLVED, IGNORED

        @JsonProperty("review_notes")
        String reviewNotes
) {
}

