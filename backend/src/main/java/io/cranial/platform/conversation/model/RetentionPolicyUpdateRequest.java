package io.cranial.platform.conversation.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record RetentionPolicyUpdateRequest(
        @Min(1) @Max(3650) Integer retentionDays,
        Boolean autoPruneEnabled,
        Boolean exportOnPrune,
        String defaultLocale,
        Boolean enforceSafetyGuard,
        @Min(1) @Max(200) Integer maxContextTurns,
        @Min(512) @Max(32768) Integer maxContextTokens
) {}

