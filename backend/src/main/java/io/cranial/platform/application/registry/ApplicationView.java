package io.cranial.platform.application.registry;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ApplicationView(
        UUID id,
        String code,
        String environment,
        String name,
        String description,
        String status,
        List<String> allowedOrigins,
        SettingsSummary settings,
        Instant createdAt,
        Instant updatedAt
) {
    public record SettingsSummary(
            int rateLimitRpm,
            int concurrencyLimit,
            int maxInputBytes,
            boolean requireSignedContext,
            boolean allowHostedProviders,
            boolean allowSensitiveExternal,
            String webhookUrl,
            boolean webhookSecretConfigured
    ) {
    }
}

