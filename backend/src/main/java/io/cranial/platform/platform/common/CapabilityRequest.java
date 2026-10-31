package io.cranial.platform.platform.common;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record CapabilityRequest(
        @Size(max = 120) String requestId,
        @NotBlank @Size(max = 80) String applicationId,
        @NotBlank @Size(max = 80) String capability,
        @NotBlank @Size(max = 120) String task,
        @NotBlank @Size(max = 32) String schemaVersion,
        Map<String, Object> input,
        Map<String, Object> context,
        Map<String, Object> options
) {
}

