package io.cranial.platform.decision.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record RecommendationApprovalRequest(
        @NotBlank
        @JsonProperty("decision")
        String decision, // APPROVED, REJECTED, EXECUTED_SIMULATION

        @JsonProperty("review_notes")
        String reviewNotes
) {
}

