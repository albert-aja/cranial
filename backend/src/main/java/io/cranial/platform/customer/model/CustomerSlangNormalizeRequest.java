package io.cranial.platform.customer.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record CustomerSlangNormalizeRequest(
        @NotBlank(message = "Text cannot be blank")
        @JsonProperty("text") String text,

        @JsonProperty("locale") String locale,

        @JsonProperty("redact_pii") Boolean redactPii
) {}

