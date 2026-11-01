package io.cranial.platform.conversation.model;

public record NormalizedSlangToken(
        String originalToken,
        String normalizedToken,
        String category,
        String explanation
) {}

