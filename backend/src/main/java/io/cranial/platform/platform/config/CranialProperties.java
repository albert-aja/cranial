package io.cranial.platform.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "cranial")
public record CranialProperties(
        String environment,
        CorsProperties cors,
        SecurityProperties security,
        BootstrapProperties bootstrap
) {
    public record CorsProperties(List<String> allowedOrigins) {
    }

    public record SecurityProperties(
            String apiKeyPepper,
            String encryptionKey,
            String adminJwtSecret,
            long adminJwtTtlMinutes,
            long contextMaxSkewSeconds,
            int adminLoginRpm
    ) {
    }

    public record BootstrapProperties(
            String adminEmail,
            String adminPassword,
            String adminName
    ) {
    }
}

