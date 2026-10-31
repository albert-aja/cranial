package io.cranial.platform.document.model;

import java.util.List;
import java.util.UUID;

public record DocumentComparisonResult(
        UUID leftDocumentId,
        UUID rightDocumentId,
        String leftFileName,
        String rightFileName,
        boolean identicalBytes,
        double textSimilarityScore,
        int pageCountDelta,
        List<FieldChange> fieldChanges,
        List<String> lineItemChanges,
        List<String> textDiffSummary
) {
    public record FieldChange(
            String fieldName,
            String changeType,
            Object leftValue,
            Object rightValue
    ) {
    }
}

