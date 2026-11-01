package io.cranial.platform.conversation.model;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import java.util.UUID;

public record ConversationCreateRequest(
        UUID tenantId,
        @NotBlank(message = "userId is required")
        String userId,
        String title,
        String locale,
        String initialMessage,
        Map<String, Object> metadata
) {}

