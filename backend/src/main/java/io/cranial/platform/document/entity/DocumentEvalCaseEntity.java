package io.cranial.platform.document.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;
import java.util.UUID;

@TableName("ai_document_eval_cases")
public class DocumentEvalCaseEntity {

    @TableId(type = IdType.INPUT)
    private UUID id;
    private String caseCode;
    private String name;
    private String documentType;
    private String ocrLanguage;
    private String sampleText;
    private String expectedFieldsJson;
    private Integer expectedLineItemCount;
    private String sourceType;
    private Boolean isActive;
    private Instant createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getCaseCode() { return caseCode; }
    public void setCaseCode(String caseCode) { this.caseCode = caseCode; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDocumentType() { return documentType; }
    public void setDocumentType(String documentType) { this.documentType = documentType; }

    public String getOcrLanguage() { return ocrLanguage; }
    public void setOcrLanguage(String ocrLanguage) { this.ocrLanguage = ocrLanguage; }

    public String getSampleText() { return sampleText; }
    public void setSampleText(String sampleText) { this.sampleText = sampleText; }

    public String getExpectedFieldsJson() { return expectedFieldsJson; }
    public void setExpectedFieldsJson(String expectedFieldsJson) { this.expectedFieldsJson = expectedFieldsJson; }

    public Integer getExpectedLineItemCount() { return expectedLineItemCount; }
    public void setExpectedLineItemCount(Integer expectedLineItemCount) { this.expectedLineItemCount = expectedLineItemCount; }

    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}

