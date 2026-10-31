package io.cranial.platform.customer.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record CustomerEvalCaseRequest(
        @NotBlank(message = "Suite code cannot be blank")
        @JsonProperty("suite_code") String suiteCode,

        @JsonProperty("locale") String locale,

        @NotBlank(message = "Input text cannot be blank")
        @JsonProperty("input_text") String inputText,

        @JsonProperty("expected_category") String expectedCategory,
        @JsonProperty("expected_sentiment") String expectedSentiment,
        @JsonProperty("expected_escalation") Boolean expectedEscalation,
        @JsonProperty("expected_promise_violation") Boolean expectedPromiseViolation,
        @JsonProperty("notes") String notes
) {}

