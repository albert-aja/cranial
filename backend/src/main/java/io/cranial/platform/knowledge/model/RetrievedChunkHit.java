package io.cranial.platform.knowledge.model;

import java.util.List;
import java.util.Map;

public record RetrievedChunkHit(
        int rank,
        int preRerankRank,
        double finalScore,
        double vectorScore,
        double keywordScore,
        double hybridScore,
        double rerankBoost,
        String content,
        String expandedContext,
        List<String> matchedTerms,
        Map<String, Object> metadata,
        SourceCitation citation
) {
}
