package io.cranial.platform.customer.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.UUID;

public record TicketTriageResult(
        @JsonProperty("ticket_id") UUID ticketId,
        @JsonProperty("ticket_number") String ticketNumber,
        @JsonProperty("category") TicketCategory category,
        @JsonProperty("priority") CustomerPriority priority,
        @JsonProperty("urgency") CustomerUrgency urgency,
        @JsonProperty("sentiment") CustomerSentiment sentiment,
        @JsonProperty("sentiment_score") double sentimentScore,
        @JsonProperty("routing_queue") RoutingQueue routingQueue,
        @JsonProperty("redacted_message") String redactedMessage,
        @JsonProperty("pii_detected") boolean piiDetected,
        @JsonProperty("pii_entities") List<PiiEntityView> piiEntities,
        @JsonProperty("promise_guard_flagged") boolean promiseGuardFlagged,
        @JsonProperty("promise_guard_reasons") List<String> promiseGuardReasons,
        @JsonProperty("suggested_canned_response") String suggestedCannedResponse,
        @JsonProperty("confidence_score") double confidenceScore,
        @JsonProperty("escalation_recommended") boolean escalationRecommended,
        @JsonProperty("escalation_reason") EscalationReason escalationReason,
        @JsonProperty("escalation_package") EscalationHandoffPackage escalationPackage
) {}

