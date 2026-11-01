package io.cranial.platform.conversation.model;

import java.time.Instant;
import java.util.UUID;

public record ConversationFeedbackView(
        UUID id,
        UUID conversationId,
        UUID messageId,
        Integer rating,
        Boolean isHelpful,
        Boolean isCorrect,
        String feedbackCategory,
        String comment,
        UUID operatorId,
        Instant createdAt
) {}

