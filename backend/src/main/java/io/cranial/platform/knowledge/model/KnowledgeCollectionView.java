package io.cranial.platform.knowledge.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record KnowledgeCollectionView(
        UUID id,
        String collectionCode,
        UUID applicationId,
        UUID tenantId,
        String name,
        String description,
        String collectionType,
        String visibilityScope,
        String defaultChunkStrategy,
        int chunkSizeChars,
        int chunkOverlapChars,
        String embeddingModelCode,
        int embeddingDimensions,
        int hnswM,
        int hnswEfConstruction,
        int hnswEfSearch,
        String vectorIndexRuntimeMode,
        boolean rerankByDefault,
        int freshnessTtlDays,
        Integer retentionDays,
        List<String> allowedRoles,
        Map<String, Object> metadata,
        String status,
        long sourceCount,
        long indexedSourceCount,
        long staleSourceCount,
        long chunkCount,
        long glossaryTermCount,
        Instant createdAt,
        Instant updatedAt
) {
}
