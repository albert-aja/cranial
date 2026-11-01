package io.cranial.platform.conversation.model;

import java.util.List;

public record ConversationDetailView(
        ConversationView conversation,
        List<ConversationMessageView> messages,
        List<ConversationFeedbackView> feedbacks
) {}

