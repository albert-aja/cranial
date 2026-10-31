package io.cranial.platform.provider.model;

import java.time.Instant;
import java.util.UUID;

public record UsageRecordView(
        UUID id,
        UUID applicationId,
        String correlationId,
        UUID providerId,
        UUID modelId,
        String capabilityCode,
        String taskCode,
        int promptTokens,
        int completionTokens,
        int totalTokens,
        long durationMs,
        boolean isLocal,
        int estimatedCostCents,
        String status,
        String errorMessage,
        Instant createdAt
) {
}

