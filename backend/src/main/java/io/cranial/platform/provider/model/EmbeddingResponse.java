package io.cranial.platform.provider.model;

import java.util.List;

public record EmbeddingResponse(
        String model,
        String provider,
        List<List<Double>> embeddings,
        int promptTokens,
        int totalTokens,
        long durationMs,
        boolean isLocal
) {
}

