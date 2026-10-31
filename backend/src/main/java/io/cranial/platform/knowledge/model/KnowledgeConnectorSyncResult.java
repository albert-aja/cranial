package io.cranial.platform.knowledge.model;

import java.time.Instant;
import java.util.UUID;

public record KnowledgeConnectorSyncResult(
        UUID connectorId,
        String connectorCode,
        String syncStatus,
        int httpStatusCode,
        boolean reindexed,
        String etag,
        String lastModifiedHeader,
        String targetSourceCode,
        int chunksAdded,
        int chunksReused,
        String message,
        Instant syncedAt
) {
}

