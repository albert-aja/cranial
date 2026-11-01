package io.cranial.platform.customer.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record SentimentAnalysisResult(
        @JsonProperty("raw_text") String rawText,
        @JsonProperty("redacted_text") String redactedText,
        @JsonProperty("normalized_text") String normalizedText,
        @JsonProperty("slang_normalized") boolean slangNormalized,
        @JsonProperty("slang_replacements") List<CustomerSlangReplacementView> slangReplacements,
        @JsonProperty("sentiment") CustomerSentiment sentiment,
        @JsonProperty("score") double score,
        @JsonProperty("confidence") double confidence,
        @JsonProperty("urgency") CustomerUrgency urgency,
        @JsonProperty("pii_detected") boolean piiDetected,
        @JsonProperty("pii_entities") List<PiiEntityView> piiEntities,
        @JsonProperty("aspects") List<SentimentAspect> aspects
) {}
