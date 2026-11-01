package io.cranial.platform.customer.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

public record CustomerSlangLexiconView(
        @JsonProperty("id") UUID id,
        @JsonProperty("slang_term") String slangTerm,
        @JsonProperty("canonical_form") String canonicalForm,
        @JsonProperty("category") String category,
        @JsonProperty("locale") String locale,
        @JsonProperty("banking_boundary_hint") boolean bankingBoundaryHint,
        @JsonProperty("explanation") String explanation,
        @JsonProperty("source") String source,
        @JsonProperty("is_active") boolean isActive,
        @JsonProperty("updated_at") Instant updatedAt
) {}

