package io.cranial.platform.analytics.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ClarificationPrompt(
        @JsonProperty("needs_clarification")
        boolean needsClarification,

        @JsonProperty("missing_fields")
        List<String> missingFields,

        @JsonProperty("clarification_message")
        String clarificationMessage,

        @JsonProperty("suggested_options")
        List<String> suggestedOptions
) {
}

