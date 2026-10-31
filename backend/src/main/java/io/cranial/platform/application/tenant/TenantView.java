package io.cranial.platform.application.tenant;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record TenantView(
        UUID id,
        UUID applicationId,
        String externalTenantRef,
        String name,
        String status,
        Map<String, Object> metadata,
        Instant createdAt,
        Instant updatedAt
) {
}

