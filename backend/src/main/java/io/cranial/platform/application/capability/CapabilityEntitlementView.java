package io.cranial.platform.application.capability;

import java.time.Instant;
import java.util.UUID;

public record CapabilityEntitlementView(
        UUID capabilityId,
        String capabilityCode,
        String capabilityName,
        String description,
        String platformStatus,
        boolean enabled,
        Integer quotaDailyRequests,
        Instant updatedAt
) {
}

