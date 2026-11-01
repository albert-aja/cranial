package io.cranial.platform.conversation.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ConversationMessageView(
        UUID id,
        UUID conversationId,
        ConversationRole role,
        String content,
        String rawContent,
        int tokenCount,
        List<StructuredUiBlock> structuredBlocks,
        List<SourceReferenceView> sourceReferences,
        List<String> toolsInvoked,
        String modelUsed,
        Long latencyMs,
        boolean isSafetyViolation,
        String safetyReason,
        Instant createdAt
) {}

