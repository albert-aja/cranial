package io.cranial.platform.customer.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record CustomerSlangLexiconRequest(
        @NotBlank(message = "Slang term cannot be blank")
        @JsonProperty("slang_term") String slangTerm,

        @NotBlank(message = "Canonical form cannot be blank")
        @JsonProperty("canonical_form") String canonicalForm,

        @JsonProperty("category") String category,

        @JsonProperty("locale") String locale,

        @JsonProperty("banking_boundary_hint") Boolean bankingBoundaryHint,

        @JsonProperty("explanation") String explanation,

        @JsonProperty("is_active") Boolean isActive
) {}

