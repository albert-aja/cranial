package io.cranial.platform.conversation.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ConversationView(
        UUID id,
        UUID applicationId,
        String applicationName,
        UUID tenantId,
        String userId,
        String title,
        String locale,
        ConversationStatus status,
        int totalTurnCount,
        int totalTokensUsed,
        String contextSummary,
        Map<String, Object> metadata,
        Instant createdAt,
        Instant updatedAt
) {}

