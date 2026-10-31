package io.cranial.platform.decision.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

import java.util.Map;
import java.util.UUID;

public record DecisionPlanRequest(
        @NotBlank
        @JsonProperty("plan_code")
        String planCode,

        @NotBlank
        @JsonProperty("name")
        String name,

        @NotBlank
        @JsonProperty("domain_archetype")
        String domainArchetype,

        @JsonProperty("description")
        String description,

        @JsonProperty("parameters")
        Map<String, Object> parameters,

        @JsonProperty("constraints")
        Object constraints,

        @JsonProperty("freshness_ttl_hours")
        Integer freshnessTtlHours,

        @JsonProperty("application_id")
        UUID applicationId,

        @JsonProperty("tenant_id")
        UUID tenantId
) {
}

