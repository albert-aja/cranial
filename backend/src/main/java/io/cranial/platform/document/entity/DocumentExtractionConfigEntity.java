package io.cranial.platform.document.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;
import java.util.UUID;

@TableName("ai_document_extraction_configs")
public class DocumentExtractionConfigEntity {

    @TableId(type = IdType.INPUT)
    private UUID id;
    private String configCode;
    private UUID applicationId;
    private String documentType;
    private UUID schemaId;
    private String defaultOcrLanguage;
    private Double confidenceThreshold;
    private Boolean autoReviewLowConfidence;
    private Integer defaultRetentionHours;
    private String preprocessDefaultsJson;
    private Boolean isActive;
    private Instant createdAt;
    private Instant updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getConfigCode() { return configCode; }
    public void setConfigCode(String configCode) { this.configCode = configCode; }

    public UUID getApplicationId() { return applicationId; }
    public void setApplicationId(UUID applicationId) { this.applicationId = applicationId; }

    public String getDocumentType() { return documentType; }
    public void setDocumentType(String documentType) { this.documentType = documentType; }

    public UUID getSchemaId() { return schemaId; }
    public void setSchemaId(UUID schemaId) { this.schemaId = schemaId; }

    public String getDefaultOcrLanguage() { return defaultOcrLanguage; }
    public void setDefaultOcrLanguage(String defaultOcrLanguage) { this.defaultOcrLanguage = defaultOcrLanguage; }

    public Double getConfidenceThreshold() { return confidenceThreshold; }
    public void setConfidenceThreshold(Double confidenceThreshold) { this.confidenceThreshold = confidenceThreshold; }

    public Boolean getAutoReviewLowConfidence() { return autoReviewLowConfidence; }
    public void setAutoReviewLowConfidence(Boolean autoReviewLowConfidence) { this.autoReviewLowConfidence = autoReviewLowConfidence; }

    public Integer getDefaultRetentionHours() { return defaultRetentionHours; }
    public void setDefaultRetentionHours(Integer defaultRetentionHours) { this.defaultRetentionHours = defaultRetentionHours; }

    public String getPreprocessDefaultsJson() { return preprocessDefaultsJson; }
    public void setPreprocessDefaultsJson(String preprocessDefaultsJson) { this.preprocessDefaultsJson = preprocessDefaultsJson; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

