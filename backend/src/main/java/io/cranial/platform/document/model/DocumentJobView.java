package io.cranial.platform.document.model;

import java.time.Instant;
import java.util.UUID;

public record DocumentJobView(
        UUID id,
        UUID documentId,
        UUID applicationId,
        String correlationId,
        String status,
        String pageRange,
        int totalPages,
        int processedPages,
        int failedPages,
        int lastCompletedPage,
        int attemptCount,
        int maxAttempts,
        String errorMessage,
        Instant startedAt,
        Instant completedAt,
        Instant createdAt
) {
}

