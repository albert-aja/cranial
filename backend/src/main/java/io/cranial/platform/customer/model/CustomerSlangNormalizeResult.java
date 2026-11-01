package io.cranial.platform.customer.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record CustomerSlangNormalizeResult(
        @JsonProperty("raw_text") String rawText,
        @JsonProperty("redacted_text") String redactedText,
        @JsonProperty("normalized_text") String normalizedText,
        @JsonProperty("locale") String locale,
        @JsonProperty("pii_detected") boolean piiDetected,
        @JsonProperty("pii_entities") List<PiiEntityView> piiEntities,
        @JsonProperty("slang_detected") boolean slangDetected,
        @JsonProperty("elongation_collapsed_count") int elongationCollapsedCount,
        @JsonProperty("replacement_count") int replacementCount,
        @JsonProperty("replacements") List<CustomerSlangReplacementView> replacements,
        @JsonProperty("banking_boundary_hint_detected") boolean bankingBoundaryHintDetected,
        @JsonProperty("money_management_disclaimer") String moneyManagementDisclaimer
) {}

