package io.cranial.platform.customer.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record EscalationEvaluateRequest(
        @JsonProperty("ticket_id") UUID ticketId,
        @JsonProperty("customer_name") String customerName,
        @JsonProperty("conversation_snippet") String conversationSnippet,
        @JsonProperty("sentiment_score") Double sentimentScore,
        @JsonProperty("user_explicit_handoff_requested") Boolean userExplicitHandoffRequested,
        @JsonProperty("locale") String locale
) {}

