package io.cranial.platform.knowledge.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record KnowledgeSourceEventView(
        UUID id,
        UUID collectionId,
        UUID sourceId,
        String sourceCode,
        String eventType,
        int sourceVersion,
        String previousSha256,
        String newSha256,
        int chunksAdded,
        int chunksReused,
        int chunksRemoved,
        long durationMs,
        String actor,
        Map<String, Object> details,
        Instant createdAt
) {
}

