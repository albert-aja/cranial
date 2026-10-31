package io.cranial.platform.knowledge.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record KnowledgeCollectionRequest(
        @NotBlank @JsonAlias({"collectionCode", "collection_code"}) String collectionCode,
        @NotBlank String name,
        String description,
        @JsonAlias({"applicationId", "application_id"}) UUID applicationId,
        @JsonAlias({"tenantId", "tenant_id"}) UUID tenantId,
        @JsonAlias({"collectionType", "collection_type"}) String collectionType,
        @JsonAlias({"visibilityScope", "visibility_scope"}) String visibilityScope,
        @JsonAlias({"defaultChunkStrategy", "default_chunk_strategy"}) String defaultChunkStrategy,
        @JsonAlias({"chunkSizeChars", "chunk_size_chars"}) Integer chunkSizeChars,
        @JsonAlias({"chunkOverlapChars", "chunk_overlap_chars"}) Integer chunkOverlapChars,
        @JsonAlias({"embeddingModelCode", "embedding_model_code"}) String embeddingModelCode,
        @JsonAlias({"hnswM", "hnsw_m"}) Integer hnswM,
        @JsonAlias({"hnswEfConstruction", "hnsw_ef_construction"}) Integer hnswEfConstruction,
        @JsonAlias({"hnswEfSearch", "hnsw_ef_search"}) Integer hnswEfSearch,
        @JsonAlias({"rerankByDefault", "rerank_by_default"}) Boolean rerankByDefault,
        @JsonAlias({"freshnessTtlDays", "freshness_ttl_days"}) Integer freshnessTtlDays,
        @JsonAlias({"retentionDays", "retention_days"}) Integer retentionDays,
        @JsonAlias({"allowedRoles", "allowed_roles"}) List<String> allowedRoles,
        Map<String, Object> metadata
) {
}
