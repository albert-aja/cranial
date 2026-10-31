package io.cranial.platform.document.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;
import java.util.UUID;

@TableName("ai_document_transfer_tokens")
public class DocumentTransferTokenEntity {

    @TableId(type = IdType.INPUT)
    private UUID id;
    private String tokenNonce;
    private String operation;
    private UUID applicationId;
    private UUID tenantId;
    private UUID documentId;
    private String fileName;
    private String allowedMimeType;
    private Long maxBytes;
    private Integer usedCount;
    private Integer maxUses;
    private Instant expiresAt;
    private Instant consumedAt;
    private Instant createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getTokenNonce() { return tokenNonce; }
    public void setTokenNonce(String tokenNonce) { this.tokenNonce = tokenNonce; }

    public String getOperation() { return operation; }
    public void setOperation(String operation) { this.operation = operation; }

    public UUID getApplicationId() { return applicationId; }
    public void setApplicationId(UUID applicationId) { this.applicationId = applicationId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getDocumentId() { return documentId; }
    public void setDocumentId(UUID documentId) { this.documentId = documentId; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getAllowedMimeType() { return allowedMimeType; }
    public void setAllowedMimeType(String allowedMimeType) { this.allowedMimeType = allowedMimeType; }

    public Long getMaxBytes() { return maxBytes; }
    public void setMaxBytes(Long maxBytes) { this.maxBytes = maxBytes; }

    public Integer getUsedCount() { return usedCount; }
    public void setUsedCount(Integer usedCount) { this.usedCount = usedCount; }

    public Integer getMaxUses() { return maxUses; }
    public void setMaxUses(Integer maxUses) { this.maxUses = maxUses; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public Instant getConsumedAt() { return consumedAt; }
    public void setConsumedAt(Instant consumedAt) { this.consumedAt = consumedAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}

