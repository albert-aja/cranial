package io.cranial.platform.document.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.UUID;

public record DocumentProcessRequest(
        @NotBlank @JsonAlias({"fileName", "file_name"}) String fileName,
        @JsonAlias({"mimeType", "mime_type"}) String mimeType,
        @JsonAlias({"contentBase64", "content_base64"}) String contentBase64,
        @JsonAlias({"rawText", "raw_text"}) String rawText,
        @JsonAlias({"documentTypeHint", "document_type_hint"}) String documentTypeHint,
        @JsonAlias({"ocrLanguage", "ocr_language"}) String ocrLanguage,
        @JsonAlias({"pageRange", "page_range"}) String pageRange,
        PreprocessOptions preprocess,
        @JsonAlias({"extractionSchemaCode", "extraction_schema_code"}) String extractionSchemaCode,
        @JsonAlias({"retentionHours", "retention_hours"}) Integer retentionHours,
        Boolean async,
        @JsonAlias({"applicationId", "application_id"}) UUID applicationId,
        @JsonAlias({"simulateFailPages", "simulate_fail_pages"}) List<Integer> simulateFailPages
) {
    public String effectiveOcrLanguage() {
        if (ocrLanguage == null || ocrLanguage.isBlank()) {
            return "eng+ind";
        }
        String clean = ocrLanguage.trim().toLowerCase();
        return switch (clean) {
            case "en", "eng", "english" -> "eng";
            case "id", "in", "ind", "indonesia", "indonesian" -> "ind";
            case "eng+ind", "ind+eng", "en+id", "id+en", "auto", "bilingual" -> "eng+ind";
            default -> "eng+ind";
        };
    }
}

