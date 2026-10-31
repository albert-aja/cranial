package io.cranial.platform.provider.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record EmbeddingRequest(
        @NotBlank String capability,
        @NotEmpty List<String> inputs,
        @JsonAlias({"modelOverride", "model_override"}) String modelOverride,
        @JsonAlias({"dataSensitivity", "data_sensitivity"}) String dataSensitivity
) {
    public EmbeddingRequest {
        if (dataSensitivity == null || dataSensitivity.isBlank()) {
            dataSensitivity = "INTERNAL";
        }
    }
}
