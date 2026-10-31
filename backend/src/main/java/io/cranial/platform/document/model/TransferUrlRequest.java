package io.cranial.platform.document.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;

public record TransferUrlRequest(
        @NotBlank @JsonAlias({"fileName", "file_name"}) String fileName,
        @JsonAlias({"mimeType", "mime_type"}) String mimeType,
        @JsonAlias({"maxBytes", "max_bytes"}) Long maxBytes,
        @JsonAlias({"ttlMinutes", "ttl_minutes"}) Integer ttlMinutes,
        @JsonAlias({"ocrLanguage", "ocr_language"}) String ocrLanguage,
        @JsonAlias({"documentTypeHint", "document_type_hint"}) String documentTypeHint
) {
}

