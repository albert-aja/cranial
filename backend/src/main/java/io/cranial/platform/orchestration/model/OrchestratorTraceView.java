package io.cranial.platform.orchestration.model;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrchestratorTraceView(
        UUID id,
        @JsonAlias({"applicationId", "application_id"}) UUID applicationId,
        @JsonAlias({"tenantId", "tenant_id"}) UUID tenantId,
        @JsonAlias({"correlationId", "correlation_id"}) String correlationId,
        @JsonAlias({"taskCode", "task_code"}) String taskCode,
        @JsonAlias({"capabilityCode", "capability_code"}) String capabilityCode,
        @JsonAlias({"modelId", "model_id"}) UUID modelId,
        @JsonAlias({"modelIdentifier", "model_identifier"}) String modelIdentifier,
        @JsonAlias({"promptId", "prompt_id"}) UUID promptId,
        @JsonAlias({"promptCode", "prompt_code"}) String promptCode,
        @JsonAlias({"schemaId", "schema_id"}) UUID schemaId,
        @JsonAlias({"schemaCode", "schema_code"}) String schemaCode,
        String status,
        @JsonAlias({"totalDurationMs", "total_duration_ms"}) long totalDurationMs,
        @JsonAlias({"promptTokens", "prompt_tokens"}) int promptTokens,
        @JsonAlias({"completionTokens", "completion_tokens"}) int completionTokens,
        @JsonAlias({"toolInvocationsCount", "tool_invocations_count"}) int toolInvocationsCount,
        List<OrchestrationStep> steps,
        @JsonAlias({"errorMessage", "error_message"}) String errorMessage,
        @JsonAlias({"createdAt", "created_at"}) Instant createdAt
) {
}

