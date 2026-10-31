package io.cranial.platform.security;

import java.util.List;
import java.util.UUID;

public record AdminPrincipal(
        UUID userId,
        String email,
        String displayName,
        List<String> roles,
        List<String> permissions
) {
}

