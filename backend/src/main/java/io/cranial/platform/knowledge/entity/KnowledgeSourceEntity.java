package io.cranial.platform.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;
import java.util.UUID;

@TableName("ai_knowledge_sources")
public class KnowledgeSourceEntity {

    @TableId(type = IdType.INPUT)
    private UUID id;
    private UUID collectionId;
    private UUID applicationId;
    private UUID tenantId;
    private String sourceCode;
    private String title;
    private String sourceUri;
    private String contentType;
    private String chunkStrategyOverride;
    private String rawContent;
    private String contentSha256;
    private Integer version;
    private String indexingStatus;
    private String errorDetail;
    private Integer chunkCount;
    private Integer tokenCount;
    private String permissionScope;
    private String allowedRolesJson;
    private String allowedUserRefsJson;
    private String metadataJson;
    private Instant effectiveUpdatedAt;
    private Instant lastIndexedAt;
    private Instant expiresAt;
    private Instant createdAt;
    private Instant updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getCollectionId() { return collectionId; }
    public void setCollectionId(UUID collectionId) { this.collectionId = collectionId; }

    public UUID getApplicationId() { return applicationId; }
    public void setApplicationId(UUID applicationId) { this.applicationId = applicationId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getSourceCode() { return sourceCode; }
    public void setSourceCode(String sourceCode) { this.sourceCode = sourceCode; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getSourceUri() { return sourceUri; }
    public void setSourceUri(String sourceUri) { this.sourceUri = sourceUri; }

    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }

    public String getChunkStrategyOverride() { return chunkStrategyOverride; }
    public void setChunkStrategyOverride(String chunkStrategyOverride) { this.chunkStrategyOverride = chunkStrategyOverride; }

    public String getRawContent() { return rawContent; }
    public void setRawContent(String rawContent) { this.rawContent = rawContent; }

    public String getContentSha256() { return contentSha256; }
    public void setContentSha256(String contentSha256) { this.contentSha256 = contentSha256; }

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }

    public String getIndexingStatus() { return indexingStatus; }
    public void setIndexingStatus(String indexingStatus) { this.indexingStatus = indexingStatus; }

    public String getErrorDetail() { return errorDetail; }
    public void setErrorDetail(String errorDetail) { this.errorDetail = errorDetail; }

    public Integer getChunkCount() { return chunkCount; }
    public void setChunkCount(Integer chunkCount) { this.chunkCount = chunkCount; }

    public Integer getTokenCount() { return tokenCount; }
    public void setTokenCount(Integer tokenCount) { this.tokenCount = tokenCount; }

    public String getPermissionScope() { return permissionScope; }
    public void setPermissionScope(String permissionScope) { this.permissionScope = permissionScope; }

    public String getAllowedRolesJson() { return allowedRolesJson; }
    public void setAllowedRolesJson(String allowedRolesJson) { this.allowedRolesJson = allowedRolesJson; }

    public String getAllowedUserRefsJson() { return allowedUserRefsJson; }
    public void setAllowedUserRefsJson(String allowedUserRefsJson) { this.allowedUserRefsJson = allowedUserRefsJson; }

    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }

    public Instant getEffectiveUpdatedAt() { return effectiveUpdatedAt; }
    public void setEffectiveUpdatedAt(Instant effectiveUpdatedAt) { this.effectiveUpdatedAt = effectiveUpdatedAt; }

    public Instant getLastIndexedAt() { return lastIndexedAt; }
    public void setLastIndexedAt(Instant lastIndexedAt) { this.lastIndexedAt = lastIndexedAt; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

