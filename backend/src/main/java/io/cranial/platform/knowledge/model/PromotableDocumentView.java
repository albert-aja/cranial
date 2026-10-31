package io.cranial.platform.knowledge.model;

import java.time.Instant;
import java.util.UUID;

public record PromotableDocumentView(
        UUID id,
        UUID applicationId,
        UUID tenantId,
        String fileName,
        String mimeType,
        String documentType,
        String ocrLanguage,
        int pageCount,
        int extractedPageCount,
        String status,
        Instant createdAt
) {
}

