package io.cranial.platform.document.model;

public record FieldProvenance(
        int pageNumber,
        int lineNumber,
        int charStart,
        int charEnd,
        String regionHint,
        String rawSnippet,
        String sourceEngine
) {
}

