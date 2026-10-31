package io.cranial.platform.customer.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record SentimentAnalysisResult(
        @JsonProperty("raw_text") String rawText,
        @JsonProperty("redacted_text") String redactedText,
        @JsonProperty("sentiment") CustomerSentiment sentiment,
        @JsonProperty("score") double score,
        @JsonProperty("confidence") double confidence,
        @JsonProperty("urgency") CustomerUrgency urgency,
        @JsonProperty("pii_detected") boolean piiDetected,
        @JsonProperty("pii_entities") List<PiiEntityView> piiEntities,
        @JsonProperty("aspects") List<SentimentAspect> aspects
) {}

