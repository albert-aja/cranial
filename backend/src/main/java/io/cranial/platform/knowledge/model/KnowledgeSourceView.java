package io.cranial.platform.knowledge.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record KnowledgeSourceView(
        UUID id,
        UUID collectionId,
        String collectionCode,
        UUID applicationId,
        UUID tenantId,
        String sourceCode,
        String title,
        String sourceUri,
        String contentType,
        String chunkStrategyOverride,
        String contentSha256,
        int version,
        String indexingStatus,
        String errorDetail,
        int chunkCount,
        int tokenCount,
        String permissionScope,
        List<String> allowedRoles,
        List<String> allowedUserRefs,
        Map<String, Object> metadata,
        boolean stale,
        long ageDays,
        String lastIndexOutcome,
        int lastChunksAdded,
        int lastChunksReused,
        int lastChunksRemoved,
        Instant effectiveUpdatedAt,
        Instant lastIndexedAt,
        Instant expiresAt,
        Instant createdAt,
        Instant updatedAt
) {
}

