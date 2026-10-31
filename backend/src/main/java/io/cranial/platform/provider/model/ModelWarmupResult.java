package io.cranial.platform.provider.model;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.util.UUID;

public record ModelWarmupResult(
        @JsonAlias({"modelId", "model_id"}) UUID modelId,
        @JsonAlias({"modelIdentifier", "model_identifier"}) String modelIdentifier,
        boolean success,
        @JsonAlias({"durationMs", "duration_ms"}) long durationMs,
        String message
) {
}

