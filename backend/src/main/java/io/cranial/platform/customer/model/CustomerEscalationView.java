package io.cranial.platform.customer.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CustomerEscalationView(
        @JsonProperty("id") UUID id,
        @JsonProperty("ticket_id") UUID ticketId,
        @JsonProperty("ticket_number") String ticketNumber,
        @JsonProperty("customer_name") String customerName,
        @JsonProperty("subject") String subject,
        @JsonProperty("application_id") UUID applicationId,
        @JsonProperty("tenant_id") UUID tenantId,
        @JsonProperty("escalation_reason") EscalationReason escalationReason,
        @JsonProperty("executive_summary") String executiveSummary,
        @JsonProperty("customer_sentiment_summary") String customerSentimentSummary,
        @JsonProperty("attempts_made") String attemptsMade,
        @JsonProperty("recommended_actions") List<String> recommendedActions,
        @JsonProperty("status") EscalationStatus status,
        @JsonProperty("assigned_agent") String assignedAgent,
        @JsonProperty("resolution_notes") String resolutionNotes,
        @JsonProperty("resolved_at") Instant resolvedAt,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("updated_at") Instant updatedAt
) {}

