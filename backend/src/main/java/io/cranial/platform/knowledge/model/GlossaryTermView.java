package io.cranial.platform.knowledge.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record GlossaryTermView(
        UUID id,
        UUID collectionId,
        UUID sourceId,
        UUID chunkId,
        String term,
        String normalizedTerm,
        List<String> aliases,
        String locale,
        String category,
        String shortDefinition,
        List<String> relatedTerms,
        Instant createdAt
) {
}

