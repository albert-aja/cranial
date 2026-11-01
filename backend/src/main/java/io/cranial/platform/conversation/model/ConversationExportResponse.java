package io.cranial.platform.conversation.model;

import java.time.Instant;
import java.util.UUID;

public record ConversationExportResponse(
        UUID conversationId,
        String title,
        String format,
        String content,
        Instant exportedAt
) {}

