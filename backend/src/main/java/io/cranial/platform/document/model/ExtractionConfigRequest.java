package io.cranial.platform.document.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record ExtractionConfigRequest(
        @NotBlank @JsonAlias({"configCode", "config_code"}) String configCode,
        @JsonAlias({"applicationId", "application_id"}) UUID applicationId,
        @NotBlank @JsonAlias({"documentType", "document_type"}) String documentType,
        @NotBlank @JsonAlias({"schemaCode", "schema_code"}) String schemaCode,
        @JsonAlias({"defaultOcrLanguage", "default_ocr_language"}) String defaultOcrLanguage,
        @JsonAlias({"confidenceThreshold", "confidence_threshold"}) Double confidenceThreshold,
        @JsonAlias({"autoReviewLowConfidence", "auto_review_low_confidence"}) Boolean autoReviewLowConfidence,
        @JsonAlias({"defaultRetentionHours", "default_retention_hours"}) Integer defaultRetentionHours
) {
}

