package io.cranial.platform.document.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DocumentView(
        UUID id,
        UUID applicationId,
        UUID tenantId,
        String fileName,
        String mimeType,
        String detectedMimeType,
        long fileSizeBytes,
        String sha256Hash,
        int pageCount,
        String documentType,
        Double classificationConfidence,
        String ocrLanguage,
        boolean isDuplicate,
        UUID duplicateOfDocumentId,
        String duplicateType,
        String status,
        Instant retentionUntil,
        Instant createdAt,
        DocumentJobView latestJob,
        DocumentExtractionView extraction,
        List<DocumentPageView> pages
) {
}

