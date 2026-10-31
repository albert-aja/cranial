package io.cranial.platform.customer.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

import java.util.Map;

public record SentimentAnalysisRequest(
        @NotBlank(message = "Text cannot be blank")
        @JsonProperty("text") String text,

        @JsonProperty("locale") String locale,

        @JsonProperty("redact_pii") Boolean redactPii,

        @JsonProperty("metadata") Map<String, Object> metadata
) {}

