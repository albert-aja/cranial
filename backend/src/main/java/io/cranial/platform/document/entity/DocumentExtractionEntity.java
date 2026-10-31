package io.cranial.platform.document.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;
import java.util.UUID;

@TableName("ai_document_extractions")
public class DocumentExtractionEntity {

    @TableId(type = IdType.INPUT)
    private UUID id;
    private UUID documentId;
    private UUID jobId;
    private UUID schemaId;
    private String schemaCode;
    private String documentType;
    private Double overallConfidence;
    private Boolean schemaValid;
    private String validationErrorsJson;
    private String extractedFieldsJson;
    private String fieldDetailsJson;
    private String tablesJson;
    private String lineItemsJson;
    private String warningsJson;
    private String reviewStatus;
    private String reviewReasonsJson;
    private String reviewedFieldsJson;
    private String reviewerActor;
    private String reviewerNotes;
    private Instant reviewedAt;
    private Boolean promotedToGroundTruth;
    private Long durationMs;
    private Instant createdAt;
    private Instant updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getDocumentId() { return documentId; }
    public void setDocumentId(UUID documentId) { this.documentId = documentId; }

    public UUID getJobId() { return jobId; }
    public void setJobId(UUID jobId) { this.jobId = jobId; }

    public UUID getSchemaId() { return schemaId; }
    public void setSchemaId(UUID schemaId) { this.schemaId = schemaId; }

    public String getSchemaCode() { return schemaCode; }
    public void setSchemaCode(String schemaCode) { this.schemaCode = schemaCode; }

    public String getDocumentType() { return documentType; }
    public void setDocumentType(String documentType) { this.documentType = documentType; }

    public Double getOverallConfidence() { return overallConfidence; }
    public void setOverallConfidence(Double overallConfidence) { this.overallConfidence = overallConfidence; }

    public Boolean getSchemaValid() { return schemaValid; }
    public void setSchemaValid(Boolean schemaValid) { this.schemaValid = schemaValid; }

    public String getValidationErrorsJson() { return validationErrorsJson; }
    public void setValidationErrorsJson(String validationErrorsJson) { this.validationErrorsJson = validationErrorsJson; }

    public String getExtractedFieldsJson() { return extractedFieldsJson; }
    public void setExtractedFieldsJson(String extractedFieldsJson) { this.extractedFieldsJson = extractedFieldsJson; }

    public String getFieldDetailsJson() { return fieldDetailsJson; }
    public void setFieldDetailsJson(String fieldDetailsJson) { this.fieldDetailsJson = fieldDetailsJson; }

    public String getTablesJson() { return tablesJson; }
    public void setTablesJson(String tablesJson) { this.tablesJson = tablesJson; }

    public String getLineItemsJson() { return lineItemsJson; }
    public void setLineItemsJson(String lineItemsJson) { this.lineItemsJson = lineItemsJson; }

    public String getWarningsJson() { return warningsJson; }
    public void setWarningsJson(String warningsJson) { this.warningsJson = warningsJson; }

    public String getReviewStatus() { return reviewStatus; }
    public void setReviewStatus(String reviewStatus) { this.reviewStatus = reviewStatus; }

    public String getReviewReasonsJson() { return reviewReasonsJson; }
    public void setReviewReasonsJson(String reviewReasonsJson) { this.reviewReasonsJson = reviewReasonsJson; }

    public String getReviewedFieldsJson() { return reviewedFieldsJson; }
    public void setReviewedFieldsJson(String reviewedFieldsJson) { this.reviewedFieldsJson = reviewedFieldsJson; }

    public String getReviewerActor() { return reviewerActor; }
    public void setReviewerActor(String reviewerActor) { this.reviewerActor = reviewerActor; }

    public String getReviewerNotes() { return reviewerNotes; }
    public void setReviewerNotes(String reviewerNotes) { this.reviewerNotes = reviewerNotes; }

    public Instant getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Instant reviewedAt) { this.reviewedAt = reviewedAt; }

    public Boolean getPromotedToGroundTruth() { return promotedToGroundTruth; }
    public void setPromotedToGroundTruth(Boolean promotedToGroundTruth) { this.promotedToGroundTruth = promotedToGroundTruth; }

    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

