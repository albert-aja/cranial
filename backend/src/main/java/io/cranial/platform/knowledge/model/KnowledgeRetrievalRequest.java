package io.cranial.platform.knowledge.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record KnowledgeRetrievalRequest(
        @JsonAlias({"collectionId", "collection_id"}) UUID collectionId,
        @JsonAlias({"collectionCode", "collection_code"}) String collectionCode,
        @NotBlank String query,
        @JsonAlias({"retrievalMode", "retrieval_mode"}) String retrievalMode,
        @JsonAlias({"topK", "top_k"}) Integer topK,
        @JsonAlias({"minScore", "min_score"}) Double minScore,
        @JsonAlias({"vectorWeight", "vector_weight"}) Double vectorWeight,
        @JsonAlias({"keywordWeight", "keyword_weight"}) Double keywordWeight,
        Boolean rerank,
        @JsonAlias({"includeStale", "include_stale"}) Boolean includeStale,
        @JsonAlias({"expandParentContext", "expand_parent_context"}) Boolean expandParentContext,
        @JsonAlias({"metadataFilters", "metadata_filters"}) Map<String, Object> metadataFilters,
        Boolean debug,
        @JsonAlias({"simulatedRoles", "simulated_roles"}) List<String> simulatedRoles,
        @JsonAlias({"simulatedUserRef", "simulated_user_ref"}) String simulatedUserRef,
        @JsonAlias({"applicationId", "application_id"}) UUID applicationId
) {
}
