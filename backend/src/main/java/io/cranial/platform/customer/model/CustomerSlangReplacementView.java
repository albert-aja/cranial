package io.cranial.platform.customer.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CustomerSlangReplacementView(
        @JsonProperty("original_token") String originalToken,
        @JsonProperty("canonical_token") String canonicalToken,
        @JsonProperty("category") String category,
        @JsonProperty("banking_boundary_hint") boolean bankingBoundaryHint,
        @JsonProperty("explanation") String explanation
) {}

