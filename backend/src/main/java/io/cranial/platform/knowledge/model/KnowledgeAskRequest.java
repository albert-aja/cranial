package io.cranial.platform.knowledge.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record KnowledgeAskRequest(
        @JsonAlias({"collectionId", "collection_id"}) UUID collectionId,
        @JsonAlias({"collectionCode", "collection_code"}) String collectionCode,
        @NotBlank String question,
        @JsonAlias({"locale", "language", "lang"}) String locale,
        @JsonAlias({"retrievalMode", "retrieval_mode"}) String retrievalMode,
        @JsonAlias({"topK", "top_k"}) Integer topK,
        Boolean rerank,
        @JsonAlias({"expandParentContext", "expand_parent_context"}) Boolean expandParentContext,
        @JsonAlias({"metadataFilters", "metadata_filters"}) Map<String, Object> metadataFilters,
        @JsonAlias({"modelOverride", "model_override"}) String modelOverride,
        Boolean debug,
        @JsonAlias({"simulatedRoles", "simulated_roles"}) List<String> simulatedRoles,
        @JsonAlias({"simulatedUserRef", "simulated_user_ref"}) String simulatedUserRef,
        @JsonAlias({"applicationId", "application_id"}) UUID applicationId
) {
}
