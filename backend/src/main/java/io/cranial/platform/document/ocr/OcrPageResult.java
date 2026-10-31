package io.cranial.platform.document.ocr;

import java.util.Map;

public record OcrPageResult(
        String text,
        double confidence,
        String engineUsed,
        Map<String, Object> diagnostics
) {
}

