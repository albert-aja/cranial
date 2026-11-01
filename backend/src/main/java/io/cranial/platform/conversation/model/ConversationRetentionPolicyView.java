package io.cranial.platform.conversation.model;

import java.time.Instant;
import java.util.UUID;

public record ConversationRetentionPolicyView(
        UUID id,
        UUID applicationId,
        String applicationName,
        int retentionDays,
        boolean autoPruneEnabled,
        boolean exportOnPrune,
        String defaultLocale,
        boolean enforceSafetyGuard,
        int maxContextTurns,
        int maxContextTokens,
        Instant createdAt,
        Instant updatedAt
) {}

