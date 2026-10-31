package io.cranial.platform.orchestration.model;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.time.Instant;
import java.util.UUID;

public record ToolView(
        UUID id,
        @JsonAlias({"toolCode", "tool_code"}) String toolCode,
        String name,
        String description,
        @JsonAlias({"applicationId", "application_id"}) UUID applicationId,
        @JsonAlias({"inputSchemaId", "input_schema_id"}) UUID inputSchemaId,
        @JsonAlias({"inputSchemaCode", "input_schema_code"}) String inputSchemaCode,
        @JsonAlias({"outputSchemaId", "output_schema_id"}) UUID outputSchemaId,
        @JsonAlias({"outputSchemaCode", "output_schema_code"}) String outputSchemaCode,
        @JsonAlias({"executionType", "execution_type"}) String executionType,
        @JsonAlias({"endpointUrl", "endpoint_url"}) String endpointUrl,
        @JsonAlias({"isReadOnly", "is_read_only"}) boolean isReadOnly,
        @JsonAlias({"requiredPermission", "required_permission"}) String requiredPermission,
        @JsonAlias({"riskLevel", "risk_level"}) String riskLevel,
        @JsonAlias({"timeoutMs", "timeout_ms"}) int timeoutMs,
        String status,
        @JsonAlias({"createdAt", "created_at"}) Instant createdAt,
        @JsonAlias({"updatedAt", "updated_at"}) Instant updatedAt
) {
}

