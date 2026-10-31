package io.cranial.platform.document.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record DocumentExtractionView(
        UUID id,
        UUID documentId,
        UUID jobId,
        String schemaCode,
        String documentType,
        double overallConfidence,
        boolean schemaValid,
        List<String> validationErrors,
        Map<String, Object> extractedFields,
        Map<String, ExtractedFieldValue> fieldDetails,
        List<ExtractedTable> tables,
        List<ExtractedLineItem> lineItems,
        List<String> warnings,
        String reviewStatus,
        List<String> reviewReasons,
        Map<String, Object> reviewedFields,
        String reviewerActor,
        String reviewerNotes,
        Instant reviewedAt,
        boolean promotedToGroundTruth,
        long durationMs,
        Instant createdAt
) {
}

