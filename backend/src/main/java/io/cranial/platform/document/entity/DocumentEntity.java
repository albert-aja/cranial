package io.cranial.platform.document.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;
import java.util.UUID;

@TableName("ai_documents")
public class DocumentEntity {

    @TableId(type = IdType.INPUT)
    private UUID id;
    private UUID applicationId;
    private UUID tenantId;
    private String externalUserRef;
    private String fileName;
    private String mimeType;
    private String detectedMimeType;
    private Long fileSizeBytes;
    private String sha256Hash;
    private String normalizedTextHash;
    private String storageKey;
    private Integer pageCount;
    private String documentType;
    private Double classificationConfidence;
    private String ocrLanguage;
    private Boolean isDuplicate;
    private UUID duplicateOfDocumentId;
    private String duplicateType;
    private String status;
    private Instant retentionUntil;
    private Instant deletedAt;
    private String metadataJson;
    private Instant createdAt;
    private Instant updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getApplicationId() { return applicationId; }
    public void setApplicationId(UUID applicationId) { this.applicationId = applicationId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getExternalUserRef() { return externalUserRef; }
    public void setExternalUserRef(String externalUserRef) { this.externalUserRef = externalUserRef; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }

    public String getDetectedMimeType() { return detectedMimeType; }
    public void setDetectedMimeType(String detectedMimeType) { this.detectedMimeType = detectedMimeType; }

    public Long getFileSizeBytes() { return fileSizeBytes; }
    public void setFileSizeBytes(Long fileSizeBytes) { this.fileSizeBytes = fileSizeBytes; }

    public String getSha256Hash() { return sha256Hash; }
    public void setSha256Hash(String sha256Hash) { this.sha256Hash = sha256Hash; }

    public String getNormalizedTextHash() { return normalizedTextHash; }
    public void setNormalizedTextHash(String normalizedTextHash) { this.normalizedTextHash = normalizedTextHash; }

    public String getStorageKey() { return storageKey; }
    public void setStorageKey(String storageKey) { this.storageKey = storageKey; }

    public Integer getPageCount() { return pageCount; }
    public void setPageCount(Integer pageCount) { this.pageCount = pageCount; }

    public String getDocumentType() { return documentType; }
    public void setDocumentType(String documentType) { this.documentType = documentType; }

    public Double getClassificationConfidence() { return classificationConfidence; }
    public void setClassificationConfidence(Double classificationConfidence) { this.classificationConfidence = classificationConfidence; }

    public String getOcrLanguage() { return ocrLanguage; }
    public void setOcrLanguage(String ocrLanguage) { this.ocrLanguage = ocrLanguage; }

    public Boolean getIsDuplicate() { return isDuplicate; }
    public void setIsDuplicate(Boolean isDuplicate) { this.isDuplicate = isDuplicate; }

    public UUID getDuplicateOfDocumentId() { return duplicateOfDocumentId; }
    public void setDuplicateOfDocumentId(UUID duplicateOfDocumentId) { this.duplicateOfDocumentId = duplicateOfDocumentId; }

    public String getDuplicateType() { return duplicateType; }
    public void setDuplicateType(String duplicateType) { this.duplicateType = duplicateType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getRetentionUntil() { return retentionUntil; }
    public void setRetentionUntil(Instant retentionUntil) { this.retentionUntil = retentionUntil; }

    public Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }

    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

