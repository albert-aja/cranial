package io.cranial.platform.orchestration.model;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.time.Instant;
import java.util.UUID;

public record TaskDefinitionView(
        UUID id,
        @JsonAlias({"taskCode", "task_code"}) String taskCode,
        String name,
        String description,
        @JsonAlias({"capabilityCode", "capability_code"}) String capabilityCode,
        @JsonAlias({"defaultPromptId", "default_prompt_id"}) UUID defaultPromptId,
        @JsonAlias({"defaultPromptCode", "default_prompt_code"}) String defaultPromptCode,
        @JsonAlias({"outputSchemaId", "output_schema_id"}) UUID outputSchemaId,
        @JsonAlias({"outputSchemaCode", "output_schema_code"}) String outputSchemaCode,
        @JsonAlias({"timeoutSeconds", "timeout_seconds"}) int timeoutSeconds,
        @JsonAlias({"allowedDataSensitivity", "allowed_data_sensitivity"}) String allowedDataSensitivity,
        @JsonAlias({"isActive", "is_active"}) boolean isActive,
        @JsonAlias({"createdAt", "created_at"}) Instant createdAt,
        @JsonAlias({"updatedAt", "updated_at"}) Instant updatedAt
) {
}

