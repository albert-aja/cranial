package io.cranial.platform.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;
import java.util.UUID;

@TableName("ai_knowledge_connectors")
public class KnowledgeConnectorEntity {

    @TableId(type = IdType.INPUT)
    private UUID id;
    private String connectorCode;
    private UUID collectionId;
    private UUID applicationId;
    private UUID tenantId;
    private String name;
    private String connectorType;
    private String feedUri;
    private String targetSourceCode;
    private String contentType;
    private String permissionScope;
    private String allowedRolesJson;
    private Integer pollIntervalMinutes;
    private String lastEtag;
    private String lastModifiedHeader;
    private String lastSyncStatus;
    private String lastSyncMessage;
    private Instant lastSyncedAt;
    private Boolean isActive;
    private Instant createdAt;
    private Instant updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getConnectorCode() { return connectorCode; }
    public void setConnectorCode(String connectorCode) { this.connectorCode = connectorCode; }

    public UUID getCollectionId() { return collectionId; }
    public void setCollectionId(UUID collectionId) { this.collectionId = collectionId; }

    public UUID getApplicationId() { return applicationId; }
    public void setApplicationId(UUID applicationId) { this.applicationId = applicationId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getConnectorType() { return connectorType; }
    public void setConnectorType(String connectorType) { this.connectorType = connectorType; }

    public String getFeedUri() { return feedUri; }
    public void setFeedUri(String feedUri) { this.feedUri = feedUri; }

    public String getTargetSourceCode() { return targetSourceCode; }
    public void setTargetSourceCode(String targetSourceCode) { this.targetSourceCode = targetSourceCode; }

    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }

    public String getPermissionScope() { return permissionScope; }
    public void setPermissionScope(String permissionScope) { this.permissionScope = permissionScope; }

    public String getAllowedRolesJson() { return allowedRolesJson; }
    public void setAllowedRolesJson(String allowedRolesJson) { this.allowedRolesJson = allowedRolesJson; }

    public Integer getPollIntervalMinutes() { return pollIntervalMinutes; }
    public void setPollIntervalMinutes(Integer pollIntervalMinutes) { this.pollIntervalMinutes = pollIntervalMinutes; }

    public String getLastEtag() { return lastEtag; }
    public void setLastEtag(String lastEtag) { this.lastEtag = lastEtag; }

    public String getLastModifiedHeader() { return lastModifiedHeader; }
    public void setLastModifiedHeader(String lastModifiedHeader) { this.lastModifiedHeader = lastModifiedHeader; }

    public String getLastSyncStatus() { return lastSyncStatus; }
    public void setLastSyncStatus(String lastSyncStatus) { this.lastSyncStatus = lastSyncStatus; }

    public String getLastSyncMessage() { return lastSyncMessage; }
    public void setLastSyncMessage(String lastSyncMessage) { this.lastSyncMessage = lastSyncMessage; }

    public Instant getLastSyncedAt() { return lastSyncedAt; }
    public void setLastSyncedAt(Instant lastSyncedAt) { this.lastSyncedAt = lastSyncedAt; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

