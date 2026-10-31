package io.cranial.platform.decision.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record DecisionPlanView(
        @JsonProperty("id")
        UUID id,

        @JsonProperty("plan_code")
        String planCode,

        @JsonProperty("application_id")
        UUID applicationId,

        @JsonProperty("tenant_id")
        UUID tenantId,

        @JsonProperty("name")
        String name,

        @JsonProperty("domain_archetype")
        String domainArchetype,

        @JsonProperty("description")
        String description,

        @JsonProperty("parameters")
        Map<String, Object> parameters,

        @JsonProperty("constraints")
        Object constraints,

        @JsonProperty("freshness_ttl_hours")
        int freshnessTtlHours,

        @JsonProperty("is_active")
        boolean isActive,

        @JsonProperty("created_at")
        Instant createdAt,

        @JsonProperty("updated_at")
        Instant updatedAt
) {
}

