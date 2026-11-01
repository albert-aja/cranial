package io.cranial.platform.conversation.model;

import java.util.List;
import java.util.UUID;

public record ConversationResponse(
        UUID conversationId,
        UUID messageId,
        String answer,
        String rawInputNormalized,
        boolean hasSlangDetected,
        boolean isSafetyViolation,
        String safetyReason,
        List<StructuredUiBlock> structuredBlocks,
        List<SourceReferenceView> sourceReferences,
        List<String> toolsInvoked,
        int tokenCount,
        long latencyMs,
        String modelUsed
) {}

