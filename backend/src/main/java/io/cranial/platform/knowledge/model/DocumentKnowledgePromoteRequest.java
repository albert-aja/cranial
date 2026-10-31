package io.cranial.platform.knowledge.model;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.util.List;
import java.util.Map;

public record DocumentKnowledgePromoteRequest(
        @JsonAlias({"sourceCode", "source_code"}) String sourceCode,
        String title,
        @JsonAlias({"contentType", "content_type"}) String contentType,
        @JsonAlias({"chunkStrategyOverride", "chunk_strategy_override"}) String chunkStrategyOverride,
        @JsonAlias({"permissionScope", "permission_scope"}) String permissionScope,
        @JsonAlias({"allowedRoles", "allowed_roles"}) List<String> allowedRoles,
        @JsonAlias({"allowedUserRefs", "allowed_user_refs"}) List<String> allowedUserRefs,
        @JsonAlias({"pageNumbers", "page_numbers"}) List<Integer> pageNumbers,
        Map<String, Object> metadata,
        @JsonAlias({"forceReindex", "force_reindex"}) Boolean forceReindex
) {
}

