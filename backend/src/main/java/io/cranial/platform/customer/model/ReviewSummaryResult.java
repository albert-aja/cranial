package io.cranial.platform.customer.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ReviewSummaryResult(
        @JsonProperty("total_analyzed") int totalAnalyzed,
        @JsonProperty("overall_sentiment") CustomerSentiment overallSentiment,
        @JsonProperty("average_sentiment_score") double averageSentimentScore,
        @JsonProperty("pros") List<String> pros,
        @JsonProperty("cons") List<String> cons,
        @JsonProperty("top_feature_requests") List<String> topFeatureRequests,
        @JsonProperty("evidence_quotes") List<String> evidenceQuotes,
        @JsonProperty("executive_summary") String executiveSummary
) {}

