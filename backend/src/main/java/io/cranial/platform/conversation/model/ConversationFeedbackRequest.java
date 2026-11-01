package io.cranial.platform.conversation.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;

public record ConversationFeedbackRequest(
        UUID messageId,
        @Min(1) @Max(5) Integer rating,
        Boolean isHelpful,
        Boolean isCorrect,
        String feedbackCategory,
        String comment
) {}

