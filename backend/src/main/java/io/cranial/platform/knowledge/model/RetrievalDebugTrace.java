package io.cranial.platform.knowledge.model;

import java.util.List;
import java.util.UUID;

public record RetrievalDebugTrace(
        List<String> normalizedQueryTerms,
        String retrievalMode,
        boolean rerankApplied,
        String embeddingModelUsed,
        long embeddingDurationMs,
        int totalChunksInCollection,
        int afterIsolationChunks,
        int permissionFilteredChunks,
        int metadataFilteredChunks,
        int belowThresholdChunks,
        int returnedHits,
        double vectorWeight,
        double keywordWeight,
        long totalDurationMs,
        List<CandidateScoreTrace> candidateTraces
) {
    public record CandidateScoreTrace(
            UUID chunkId,
            String sourceCode,
            String sectionTitle,
            Integer pageNumber,
            String permissionScope,
            boolean authorized,
            boolean metadataMatched,
            double vectorScore,
            double keywordScore,
            double hybridScore,
            double rerankBoost,
            double finalScore,
            List<String> matchedTerms,
            String filterReason
    ) {
    }
}

