package io.cranial.platform.provider.model;

import java.time.Instant;
import java.util.UUID;

public record RoutingPolicyView(
        UUID id,
        String name,
        String capabilityCode,
        String taskCode,
        UUID preferredModelId,
        String preferredModelIdentifier,
        boolean preferredModelLocal,
        UUID fallbackModelId,
        String fallbackModelIdentifier,
        boolean fallbackModelLocal,
        String allowedDataSensitivity,
        boolean isActive,
        Instant createdAt,
        Instant updatedAt
) {
}

