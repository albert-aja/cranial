package io.cranial.platform.document.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Min;

public record DocumentBatchEstimateRequest(
        @Min(1) @JsonAlias({"documentCount", "document_count"}) int documentCount,
        @Min(1) @JsonAlias({"totalBytes", "total_bytes"}) long totalBytes,
        @Min(1) @JsonAlias({"estimatedPages", "estimated_pages"}) int estimatedPages,
        @JsonAlias({"ocrEnabled", "ocr_enabled"}) Boolean ocrEnabled,
        @JsonAlias({"preprocessEnabled", "preprocess_enabled"}) Boolean preprocessEnabled,
        @JsonAlias({"llmAssistEnabled", "llm_assist_enabled"}) Boolean llmAssistEnabled
) {
}

