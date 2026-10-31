package io.cranial.platform.knowledge.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record KnowledgeSynonymView(
        UUID id,
        UUID collectionId,
        UUID applicationId,
        String canonicalTerm,
        List<String> synonyms,
        String localePair,
        String domainTag,
        boolean isActive,
        Instant createdAt
) {
}

