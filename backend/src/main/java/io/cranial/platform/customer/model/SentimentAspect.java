package io.cranial.platform.customer.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SentimentAspect(
        @JsonProperty("aspect") String aspect,
        @JsonProperty("sentiment") CustomerSentiment sentiment,
        @JsonProperty("evidence_quote") String evidenceQuote
) {}

