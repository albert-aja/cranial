package io.cranial.platform.customer.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CustomerTicketView(
        @JsonProperty("id") UUID id,
        @JsonProperty("ticket_number") String ticketNumber,
        @JsonProperty("application_id") UUID applicationId,
        @JsonProperty("tenant_id") UUID tenantId,
        @JsonProperty("external_user_ref") String externalUserRef,
        @JsonProperty("customer_name") String customerName,
        @JsonProperty("subject") String subject,
        @JsonProperty("redacted_message") String redactedMessage,
        @JsonProperty("normalized_message") String normalizedMessage,
        @JsonProperty("slang_normalized") boolean slangNormalized,
        @JsonProperty("slang_replacements") List<CustomerSlangReplacementView> slangReplacements,
        @JsonProperty("pii_detected") boolean piiDetected,
        @JsonProperty("pii_entities") List<PiiEntityView> piiEntities,
        @JsonProperty("locale") String locale,
        @JsonProperty("category") TicketCategory category,
        @JsonProperty("sentiment") CustomerSentiment sentiment,
        @JsonProperty("sentiment_score") double sentimentScore,
        @JsonProperty("urgency") CustomerUrgency urgency,
        @JsonProperty("priority") CustomerPriority priority,
        @JsonProperty("routing_queue") RoutingQueue routingQueue,
        @JsonProperty("status") TicketStatus status,
        @JsonProperty("promise_guard_flagged") boolean promiseGuardFlagged,
        @JsonProperty("promise_guard_reasons") List<String> promiseGuardReasons,
        @JsonProperty("suggested_canned_response") String suggestedCannedResponse,
        @JsonProperty("confidence_score") double confidenceScore,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("updated_at") Instant updatedAt
) {}
