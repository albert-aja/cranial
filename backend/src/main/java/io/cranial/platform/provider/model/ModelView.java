package io.cranial.platform.provider.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ModelView(
        UUID id,
        UUID providerId,
        String providerCode,
        String providerName,
        String modelIdentifier,
        String displayName,
        String modality,
        int contextWindowTokens,
        int maxOutputTokens,
        int costPerMillionInputCents,
        int costPerMillionOutputCents,
        boolean isLocal,
        String status,
        List<String> capabilities,
        String metadataJson,
        Instant createdAt,
        Instant updatedAt
) {
}

