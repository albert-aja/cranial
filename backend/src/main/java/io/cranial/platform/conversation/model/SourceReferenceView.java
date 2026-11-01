package io.cranial.platform.conversation.model;

public record SourceReferenceView(
        String sourceId,
        String title,
        String uri,
        String snippet,
        Double relevanceScore
) {}

