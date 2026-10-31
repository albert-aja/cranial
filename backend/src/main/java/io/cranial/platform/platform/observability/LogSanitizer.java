package io.cranial.platform.platform.observability;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class LogSanitizer {

    public static final String REDACTED = "[REDACTED]";

    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "password",
            "password_hash",
            "passwordhash",
            "secret",
            "secret_hash",
            "signing_secret",
            "signingsecret",
            "webhook_secret",
            "webhooksecret",
            "api_key",
            "apikey",
            "x-cranial-api-key",
            "x-cranial-signature",
            "authorization",
            "token",
            "access_token",
            "jwt",
            "pepper",
            "encryption_key"
    );

    private static final Pattern API_KEY_PATTERN = Pattern.compile("crn_[a-z]+_[a-zA-Z0-9]+\\.[a-zA-Z0-9_\\-]+");
    private static final Pattern BEARER_PATTERN = Pattern.compile("(?i)Bearer\\s+[A-Za-z0-9\\-_]+\\.[A-Za-z0-9\\-_]+\\.[A-Za-z0-9\\-_]+");

    private LogSanitizer() {
    }

    public static Map<String, Object> sanitizeMap(Map<String, Object> input) {
        if (input == null || input.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : input.entrySet()) {
            String key = entry.getKey();
            if (isSensitiveKey(key)) {
                sanitized.put(key, REDACTED);
            } else {
                sanitized.put(key, sanitizeValue(entry.getValue()));
            }
        }
        return sanitized;
    }

    @SuppressWarnings("unchecked")
    private static Object sanitizeValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String str) {
            return sanitizeString(str);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> stringKeyed = new LinkedHashMap<>();
            map.forEach((k, v) -> stringKeyed.put(String.valueOf(k), v));
            return sanitizeMap(stringKeyed);
        }
        if (value instanceof List<?> list) {
            return list.stream().map(LogSanitizer::sanitizeValue).toList();
        }
        return value;
    }

    public static String sanitizeString(String text) {
        if (text == null) {
            return null;
        }
        String masked = API_KEY_PATTERN.matcher(text).replaceAll("crn_[REDACTED]");
        return BEARER_PATTERN.matcher(masked).replaceAll("Bearer [REDACTED]");
    }

    public static boolean isSensitiveKey(String key) {
        if (key == null) {
            return false;
        }
        String normalized = key.toLowerCase(Locale.ROOT).trim();
        if (SENSITIVE_KEYS.contains(normalized)) {
            return true;
        }
        return normalized.contains("password")
                || normalized.contains("secret")
                || normalized.contains("api_key")
                || normalized.contains("apikey")
                || normalized.contains("signature")
                || normalized.endsWith("token");
    }
}

