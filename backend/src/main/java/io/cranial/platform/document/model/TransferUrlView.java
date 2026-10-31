package io.cranial.platform.document.model;

import java.time.Instant;
import java.util.UUID;

public record TransferUrlView(
        UUID tokenId,
        UUID documentId,
        String operation,
        String method,
        String url,
        long maxBytes,
        Instant expiresAt
) {
}

