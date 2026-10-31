package io.cranial.platform.knowledge.model;

import java.util.UUID;

public record SourceCitation(
        String citationId,
        UUID chunkId,
        UUID sourceId,
        String sourceCode,
        String sourceTitle,
        String sourceUri,
        int sourceVersion,
        Integer pageNumber,
        String sectionTitle,
        int charStart,
        int charEnd,
        boolean stale,
        long ageDays,
        String staleWarning
) {
}

