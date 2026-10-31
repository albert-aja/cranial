package io.cranial.platform.document.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;

import java.util.Map;

public record ExtractionReviewRequest(
        @NotBlank String decision,
        @JsonAlias({"correctedFields", "corrected_fields"}) Map<String, Object> correctedFields,
        @JsonAlias({"reviewerNotes", "reviewer_notes"}) String reviewerNotes,
        @JsonAlias({"promoteToGroundTruth", "promote_to_ground_truth"}) Boolean promoteToGroundTruth
) {
}

