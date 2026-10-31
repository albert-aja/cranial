package io.cranial.platform.provider.model;

import io.cranial.platform.provider.entity.ModelEntity;
import io.cranial.platform.provider.entity.ProviderEntity;

public record RoutingDecision(
        ModelEntity model,
        ProviderEntity provider,
        ModelEntity fallbackModel,
        ProviderEntity fallbackProvider,
        String policyName,
        boolean fallbackUsed,
        String reason
) {
}

