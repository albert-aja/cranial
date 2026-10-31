package io.cranial.platform.analytics.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AnalyticsDatasetView(
        @JsonProperty("id")
        UUID id,

        @JsonProperty("dataset_code")
        String datasetCode,

        @JsonProperty("application_id")
        UUID applicationId,

        @JsonProperty("tenant_id")
        UUID tenantId,

        @JsonProperty("name")
        String name,

        @JsonProperty("description")
        String description,

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

        @JsonProperty("data_points_count")
        int dataPointsCount,

        @JsonProperty("max_query_window_days")
        int maxQueryWindowDays,

        @JsonProperty("is_active")
        boolean isActive,

        @JsonProperty("source_freshness_at")
        Instant sourceFreshnessAt,

        @JsonProperty("created_at")
        Instant createdAt,

        @JsonProperty("updated_at")
        Instant updatedAt
) {
}

