package io.cranial.platform.knowledge.model;

import java.util.List;
import java.util.UUID;

public record KnowledgeRetrievalResult(
        UUID collectionId,
        String collectionCode,
        String query,
        String retrievalMode,
        boolean reranked,
        List<RetrievedChunkHit> hits,
        List<String> staleWarnings,
        List<String> securityWarnings,
        long durationMs,
        RetrievalDebugTrace debugTrace
) {
}

