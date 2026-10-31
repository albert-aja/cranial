package io.cranial.platform.knowledge.model;

import java.util.List;
import java.util.UUID;

public record GlossaryLinkResult(
        String text,
        int matchedCount,
        List<LinkedTermOccurrence> occurrences
) {
    public record LinkedTermOccurrence(
            UUID termId,
            String term,
            String matchedText,
            int startOffset,
            int endOffset,
            String locale,
            String category,
            String shortDefinition,
            UUID sourceId,
            UUID chunkId
    ) {
    }
}

