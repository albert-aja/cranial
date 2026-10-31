package io.cranial.platform.application.credential;

import java.time.Instant;
import java.util.UUID;

public record CredentialView(
        UUID id,
        UUID applicationId,
        String name,
        String environment,
        String keyPrefix,
        String status,
        Instant expiresAt,
        Instant lastUsedAt,
        Instant revokedAt,
        Instant createdAt
) {
}

