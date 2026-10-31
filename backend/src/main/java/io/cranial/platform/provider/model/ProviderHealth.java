package io.cranial.platform.provider.model;

import java.util.Map;

public record ProviderHealth(
        String providerCode,
        boolean reachable,
        long latencyMs,
        String message,
        Map<String, Object> details
) {
    public static ProviderHealth healthy(String providerCode, long latencyMs, String message, Map<String, Object> details) {
        return new ProviderHealth(providerCode, true, latencyMs, message, details != null ? details : Map.of());
    }

    public static ProviderHealth unreachable(String providerCode, long latencyMs, String message) {
        return new ProviderHealth(providerCode, false, latencyMs, message, Map.of());
    }
}

