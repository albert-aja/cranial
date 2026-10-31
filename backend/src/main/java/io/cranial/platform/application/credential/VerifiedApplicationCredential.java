package io.cranial.platform.application.credential;

import java.util.UUID;

public record VerifiedApplicationCredential(
        UUID credentialId,
        UUID applicationId,
        String applicationCode,
        String environment,
        String keyPrefix,
        String signingSecret,
        int rateLimitRpm,
        int maxInputBytes,
        boolean requireSignedContext,
        boolean allowHostedProviders,
        boolean allowSensitiveExternal
) {
}

