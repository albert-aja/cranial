package io.cranial.platform.knowledge.model;

import java.time.Instant;
import java.util.List;

public record KnowledgeEvalResult(
        int totalCases,
        int retrievalCasesCount,
        int hitCount,
        double hitRateAtK,
        double mrr,
        double meanPrecisionAtK,
        double citationAccuracy,
        int permissionCheckCount,
        int permissionPassCount,
        double permissionPassRate,
        long totalDurationMs,
        Instant executedAt,
        List<CaseEvaluationOutcome> cases
) {
    public record CaseEvaluationOutcome(
            String caseCode,
            String name,
            String collectionCode,
            String queryText,
            String retrievalMode,
            boolean reranked,
            List<String> simulatedRoles,
            String expectedSourceCode,
            String expectPermissionBlockedSource,
            boolean hitPassed,
            int matchedRank,
            double reciprocalRank,
            double precisionAtK,
            boolean citationAccurate,
            boolean permissionPassed,
            List<String> returnedSources,
            long durationMs
    ) {
    }
}

