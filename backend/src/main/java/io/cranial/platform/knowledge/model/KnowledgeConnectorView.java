package io.cranial.platform.knowledge.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record KnowledgeConnectorView(
        UUID id,
        String connectorCode,
        UUID collectionId,
        String collectionCode,
        UUID applicationId,
        UUID tenantId,
        String name,
        String connectorType,
        String feedUri,
        String targetSourceCode,
        String contentType,
        String permissionScope,
        List<String> allowedRoles,
        int pollIntervalMinutes,
        String lastEtag,
        String lastModifiedHeader,
        String lastSyncStatus,
        String lastSyncMessage,
        Instant lastSyncedAt,
        boolean isActive,
        Instant createdAt
) {
}

