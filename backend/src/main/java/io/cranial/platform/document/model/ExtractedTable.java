package io.cranial.platform.document.model;

import java.util.List;

public record ExtractedTable(
        int tableIndex,
        int pageNumber,
        List<String> headers,
        List<List<String>> rows,
        double confidence
) {
}

