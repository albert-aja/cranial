package io.cranial.platform.conversation.model;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;

public record ConversationMessageRequest(
        @NotBlank(message = "Content cannot be blank")
        String content,
        String locale,
        List<String> allowedTools,
        Map<String, Object> parameters
) {}

