package io.cranial.platform.knowledge.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record KnowledgeSourceIngestRequest(
        @NotBlank @JsonAlias({"sourceCode", "source_code"}) String sourceCode,
        @NotBlank String title,
        @JsonAlias({"sourceUri", "source_uri"}) String sourceUri,
        @JsonAlias({"contentType", "content_type"}) String contentType,
        @JsonAlias({"chunkStrategyOverride", "chunk_strategy_override"}) String chunkStrategyOverride,
        @NotBlank String content,
        @JsonAlias({"permissionScope", "permission_scope"}) String permissionScope,
        @JsonAlias({"allowedRoles", "allowed_roles"}) List<String> allowedRoles,
        @JsonAlias({"allowedUserRefs", "allowed_user_refs"}) List<String> allowedUserRefs,
        Map<String, Object> metadata,
        @JsonAlias({"effectiveUpdatedAt", "effective_updated_at"}) Instant effectiveUpdatedAt,
        @JsonAlias({"retentionDays", "retention_days"}) Integer retentionDays,
        @JsonAlias({"forceReindex", "force_reindex"}) Boolean forceReindex,
        @JsonAlias({"simulateFailure", "simulate_failure"}) Boolean simulateFailure
) {
}

