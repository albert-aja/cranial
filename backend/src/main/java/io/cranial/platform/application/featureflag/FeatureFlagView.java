package io.cranial.platform.application.featureflag;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record FeatureFlagView(
        UUID id,
        String flagKey,
        String name,
        String description,
        boolean enabled,
        int rolloutPercentage,
        List<String> allowedAppCodes,
        Instant updatedAt
) {
}

