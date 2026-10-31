package io.cranial.platform.knowledge.model;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record KnowledgeExportBundle(
        @JsonAlias({"bundleVersion", "bundle_version"}) String bundleVersion,
        @JsonAlias({"exportedAt", "exported_at"}) Instant exportedAt,
        ExportedCollectionSpec collection,
        List<ExportedSourceSpec> sources,
        @JsonAlias({"glossaryTerms", "glossary_terms"}) List<ExportedGlossarySpec> glossaryTerms,
        @JsonAlias({"manifestSha256", "manifest_sha256"}) String manifestSha256
) {
    public record ExportedCollectionSpec(
            @JsonAlias({"collectionCode", "collection_code"}) String collectionCode,
            String name,
            String description,
            @JsonAlias({"collectionType", "collection_type"}) String collectionType,
            @JsonAlias({"visibilityScope", "visibility_scope"}) String visibilityScope,
            @JsonAlias({"defaultChunkStrategy", "default_chunk_strategy"}) String defaultChunkStrategy,
            @JsonAlias({"chunkSizeChars", "chunk_size_chars"}) int chunkSizeChars,
            @JsonAlias({"chunkOverlapChars", "chunk_overlap_chars"}) int chunkOverlapChars,
            @JsonAlias({"embeddingModelCode", "embedding_model_code"}) String embeddingModelCode,
            @JsonAlias({"hnswM", "hnsw_m"}) Integer hnswM,
            @JsonAlias({"hnswEfConstruction", "hnsw_ef_construction"}) Integer hnswEfConstruction,
            @JsonAlias({"hnswEfSearch", "hnsw_ef_search"}) Integer hnswEfSearch,
            @JsonAlias({"rerankByDefault", "rerank_by_default"}) boolean rerankByDefault,
            @JsonAlias({"freshnessTtlDays", "freshness_ttl_days"}) int freshnessTtlDays,
            @JsonAlias({"retentionDays", "retention_days"}) Integer retentionDays,
            @JsonAlias({"allowedRoles", "allowed_roles"}) List<String> allowedRoles,
            Map<String, Object> metadata
    ) {
    }

    public record ExportedSourceSpec(
            @JsonAlias({"sourceCode", "source_code"}) String sourceCode,
            String title,
            @JsonAlias({"sourceUri", "source_uri"}) String sourceUri,
            @JsonAlias({"contentType", "content_type"}) String contentType,
            @JsonAlias({"chunkStrategyOverride", "chunk_strategy_override"}) String chunkStrategyOverride,
            String content,
            @JsonAlias({"permissionScope", "permission_scope"}) String permissionScope,
            @JsonAlias({"allowedRoles", "allowed_roles"}) List<String> allowedRoles,
            @JsonAlias({"allowedUserRefs", "allowed_user_refs"}) List<String> allowedUserRefs,
            Map<String, Object> metadata,
            @JsonAlias({"effectiveUpdatedAt", "effective_updated_at"}) Instant effectiveUpdatedAt
    ) {
    }

    public record ExportedGlossarySpec(
            String term,
            List<String> aliases,
            String locale,
            String category,
            @JsonAlias({"shortDefinition", "short_definition"}) String shortDefinition,
            @JsonAlias({"relatedTerms", "related_terms"}) List<String> relatedTerms
    ) {
    }
}
