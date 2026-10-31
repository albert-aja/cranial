package io.cranial.platform.customer.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record RecommendedFeatureView(
        @JsonProperty("feature_code") String featureCode,
        @JsonProperty("feature_name") String featureName,
        @JsonProperty("category") String category,
        @JsonProperty("match_score") double matchScore,
        @JsonProperty("rationale") String rationale,
        @JsonProperty("trade_off_notes") String tradeOffNotes,
        @JsonProperty("setup_guidance") List<String> setupGuidance
) {}

