package io.cranial.platform.conversation.model;

public record ClarificationOption(
        String id,
        String label,
        String suggestedInput,
        String description
) {}

