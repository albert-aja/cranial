package io.cranial.platform.document.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record OcrBenchmarkResult(
        int totalCases,
        int classificationMatches,
        int schemaValidCases,
        double classificationAccuracy,
        double fieldAccuracy,
        double schemaValidRate,
        long totalDurationMs,
        Instant executedAt,
        List<CaseOutcome> cases
) {
    public record CaseOutcome(
            String caseCode,
            String name,
            String sourceType,
            String expectedType,
            String predictedType,
            boolean typeMatched,
            boolean schemaValid,
            int matchedFields,
            int totalExpectedFields,
            double fieldAccuracy,
            int extractedLineItems,
            long durationMs,
            Map<String, Object> extractedFields
    ) {
    }
}

