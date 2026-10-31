package io.cranial.platform.provider.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.Map;

public record ChatCompletionRequest(
        @NotBlank String capability,
        String task,
        @NotEmpty List<ChatMessage> messages,
        Double temperature,
        @JsonAlias({"maxTokens", "max_tokens"}) Integer maxTokens,
        @JsonAlias({"modelOverride", "model_override"}) String modelOverride,
        @JsonAlias({"dataSensitivity", "data_sensitivity"}) String dataSensitivity, // PUBLIC, INTERNAL, RESTRICTED
        Map<String, Object> context,
        @JsonAlias({"locale", "language", "lang"}) String locale
) {
    public ChatCompletionRequest {
        if (dataSensitivity == null || dataSensitivity.isBlank()) {
            dataSensitivity = "INTERNAL";
        }
        if (locale == null || locale.isBlank()) {
            locale = "en";
        }
    }

    public ChatCompletionRequest(
            String capability,
            String task,
            List<ChatMessage> messages,
            Double temperature,
            Integer maxTokens,
            String modelOverride,
            String dataSensitivity,
            Map<String, Object> context
    ) {
        this(capability, task, messages, temperature, maxTokens, modelOverride, dataSensitivity, context, "en");
    }
}
