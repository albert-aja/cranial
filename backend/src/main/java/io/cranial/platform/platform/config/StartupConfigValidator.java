package io.cranial.platform.platform.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class StartupConfigValidator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupConfigValidator.class);
    private static final int MIN_SECRET_LENGTH = 32;

    private final CranialProperties properties;

    public StartupConfigValidator(CranialProperties properties) {
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        validate(properties);
        String env = properties.environment() != null ? properties.environment() : "unknown";
        boolean bootstrapConfigured = properties.bootstrap() != null
                && properties.bootstrap().adminEmail() != null
                && !properties.bootstrap().adminEmail().isBlank();
        log.info(
                "CRANIAL startup config validated: environment={}, jwtTtlMinutes={}, contextMaxSkewSeconds={}, bootstrapAdminConfigured={}",
                env,
                properties.security().adminJwtTtlMinutes(),
                properties.security().contextMaxSkewSeconds(),
                bootstrapConfigured
        );
    }

    public static void validate(CranialProperties props) {
        if (props == null || props.security() == null) {
            throw new IllegalStateException("CRANIAL security configuration block is missing");
        }
        CranialProperties.SecurityProperties sec = props.security();
        requireMinLength("cranial.security.api-key-pepper", sec.apiKeyPepper(), MIN_SECRET_LENGTH);
        requireMinLength("cranial.security.encryption-key", sec.encryptionKey(), MIN_SECRET_LENGTH);
        requireMinLength("cranial.security.admin-jwt-secret", sec.adminJwtSecret(), MIN_SECRET_LENGTH);

        if (sec.adminJwtTtlMinutes() <= 0 || sec.adminJwtTtlMinutes() > 1440) {
            throw new IllegalStateException("cranial.security.admin-jwt-ttl-minutes must be between 1 and 1440");
        }
        if (sec.contextMaxSkewSeconds() <= 0 || sec.contextMaxSkewSeconds() > 3600) {
            throw new IllegalStateException("cranial.security.context-max-skew-seconds must be between 1 and 3600");
        }

        boolean isProd = "prod".equalsIgnoreCase(props.environment()) || "production".equalsIgnoreCase(props.environment());
        if (isProd) {
            rejectDevPlaceholder("cranial.security.api-key-pepper", sec.apiKeyPepper());
            rejectDevPlaceholder("cranial.security.encryption-key", sec.encryptionKey());
            rejectDevPlaceholder("cranial.security.admin-jwt-secret", sec.adminJwtSecret());
        }
    }

    private static void requireMinLength(String key, String value, int minLen) {
        if (value == null || value.isBlank() || value.trim().length() < minLen) {
            throw new IllegalStateException(key + " must be configured with at least " + minLen + " characters");
        }
    }

    private static void rejectDevPlaceholder(String key, String value) {
        String lower = value.toLowerCase();
        if (lower.contains("dev-only") || lower.contains("replace-in-prod") || lower.contains("change-me")) {
            throw new IllegalStateException(key + " uses a development placeholder value, which is forbidden in prod");
        }
    }
}

