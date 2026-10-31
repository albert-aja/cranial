package io.cranial.platform.security;

import java.util.List;
import java.util.UUID;

public record VerifiedRequestContext(
        UUID applicationId,
        String applicationCode,
        String environment,
        UUID credentialId,
        String keyPrefix,
        boolean contextSigned,
        UUID tenantId,
        String externalTenantRef,
        String externalUserRef,
        List<String> roles,
        List<String> scopes,
        String correlationId,
        boolean allowHostedProviders,
        boolean allowSensitiveExternal
) {
}

