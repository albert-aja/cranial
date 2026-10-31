package io.cranial.platform.analytics.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Map;
import java.util.UUID;

public record AnalyticsDatasetRequest(
        @NotBlank(message = "dataset_code is required")
        @Size(min = 3, max = 64, message = "dataset_code must be between 3 and 64 characters")
        @Pattern(regexp = "^[a-z0-9_\\-]+$", message = "dataset_code must only contain lowercase alphanumeric, underscores, and hyphens")
        @JsonProperty("dataset_code")
        String datasetCode,

        @NotBlank(message = "name is required")
        @Size(max = 128)
        @JsonProperty("name")
        String name,

        @Size(max = 1000)
        @JsonProperty("description")
        String description,

        @JsonProperty("application_id")
        UUID applicationId,

        @JsonProperty("tenant_id")
        UUID tenantId,

        @NotBlank(message = "domain_archetype is required")
        @JsonProperty("domain_archetype")
        String domainArchetype,

        @JsonProperty("schema_definition")
        Map<String, Object> schemaDefinition,

        @JsonProperty("connector_type")
        String connectorType,

        @JsonProperty("source_uri")
        String sourceUri,

        @JsonProperty("refresh_policy")
        String refreshPolicy,

        @JsonProperty("data_points")
        Object dataPoints,

        @JsonProperty("max_query_window_days")
        Integer maxQueryWindowDays,

        @JsonProperty("is_active")
        Boolean isActive
) {
}

