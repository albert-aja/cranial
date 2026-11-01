package io.cranial.platform.conversation.model;

import jakarta.validation.constraints.NotBlank;

public record SlangNormalizeRequest(
        @NotBlank(message = "Text cannot be blank")
        String text,
        String locale
) {}

