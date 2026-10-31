package io.cranial.platform.document.model;

import java.time.Instant;
import java.util.UUID;

public record ExtractionConfigView(
        UUID id,
        String configCode,
        UUID applicationId,
        String documentType,
        UUID schemaId,
        String schemaCode,
        String defaultOcrLanguage,
        double confidenceThreshold,
        boolean autoReviewLowConfidence,
        int defaultRetentionHours,
        boolean isActive,
        Instant updatedAt
) {
}

