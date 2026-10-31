package io.cranial.platform.provider.model;

import java.time.Instant;
import java.util.UUID;

public record ProviderView(
        UUID id,
        String code,
        String name,
        String providerType,
        String baseUrl,
        String status,
        int timeoutSeconds,
        boolean hasApiKey,
        String configJson,
        Instant createdAt,
        Instant updatedAt
) {
}

