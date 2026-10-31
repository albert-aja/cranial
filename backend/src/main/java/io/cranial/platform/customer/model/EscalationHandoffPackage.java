package io.cranial.platform.customer.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record EscalationHandoffPackage(
        @JsonProperty("escalation_id") UUID escalationId,
        @JsonProperty("ticket_id") UUID ticketId,
        @JsonProperty("ticket_number") String ticketNumber,
        @JsonProperty("reason") EscalationReason reason,
        @JsonProperty("executive_summary") String executiveSummary,
        @JsonProperty("customer_sentiment_summary") String customerSentimentSummary,
        @JsonProperty("attempts_made") String attemptsMade,
        @JsonProperty("recommended_actions") List<String> recommendedActions,
        @JsonProperty("status") EscalationStatus status,
        @JsonProperty("created_at") Instant createdAt
) {}

