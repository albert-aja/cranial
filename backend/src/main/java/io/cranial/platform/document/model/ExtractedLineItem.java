package io.cranial.platform.document.model;

public record ExtractedLineItem(
        int index,
        String description,
        Double quantity,
        Double unitPrice,
        Double amount,
        String currency,
        int pageNumber,
        int lineNumber,
        String rawLine,
        double confidence,
        String status
) {
}

