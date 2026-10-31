package io.cranial.platform.provider.model;

import java.util.Map;

public record ChatCompletionResponse(
        String id,
        String model,
        String provider,
        ChatMessage message,
        int promptTokens,
        int completionTokens,
        int totalTokens,
        long durationMs,
        boolean isLocal,
        boolean fallbackUsed,
        Map<String, Object> metadata
) {
}

