package io.cranial.platform.document.model;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record DocumentPageView(
        UUID id,
        int pageNumber,
        String status,
        String engineUsed,
        Double ocrConfidence,
        String extractedText,
        int charCount,
        int wordCount,
        List<String> preprocessApplied,
        Map<String, Object> diagnostics,
        long durationMs
) {
}

