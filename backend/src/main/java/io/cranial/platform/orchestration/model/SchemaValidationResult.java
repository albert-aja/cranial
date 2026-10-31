package io.cranial.platform.orchestration.model;

import java.util.List;

public record SchemaValidationResult(
        boolean valid,
        List<String> errors
) {
    public static SchemaValidationResult success() {
        return new SchemaValidationResult(true, List.of());
    }

    public static SchemaValidationResult failure(List<String> errors) {
        return new SchemaValidationResult(false, errors != null ? errors : List.of("Unknown schema validation error"));
    }
}

