package io.cranial.platform.audit;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditLogView(
        UUID id,
        String correlationId,
        String actorType,
        String actorId,
        UUID applicationId,
        UUID tenantId,
        String action,
        String targetType,
        String targetId,
        String outcome,
        Map<String, Object> metadata,
        String ipAddress,
        Instant createdAt
) {
}

