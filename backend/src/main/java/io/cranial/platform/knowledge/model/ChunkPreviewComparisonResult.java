package io.cranial.platform.knowledge.model;

import java.util.List;

public record ChunkPreviewComparisonResult(
        StrategyPreviewPane left,
        StrategyPreviewPane right,
        List<String> securityWarnings
) {
    public record StrategyPreviewPane(
            String strategyUsed,
            int chunkSizeChars,
            int chunkOverlapChars,
            int totalChunks,
            int totalTokens,
            double avgTokensPerChunk,
            int maxTokensInChunk,
            List<String> glossaryTermsDetected,
            List<ChunkBoundarySlice> chunks
    ) {
    }

    public record ChunkBoundarySlice(
            int chunkIndex,
            String content,
            String parentSectionPreview,
            int tokenCount,
            Integer pageNumber,
            String sectionTitle,
            int charStart,
            int charEnd
    ) {
    }
}

