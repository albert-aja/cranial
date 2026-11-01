package io.cranial.platform.conversation.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;
import java.util.UUID;

@TableName("ai_conversations")
public class ConversationEntity {

    @TableId
    private UUID id;

    @TableField("application_id")
    private UUID applicationId;

    @TableField("tenant_id")
    private UUID tenantId;

    @TableField("user_id")
    private String userId;

    @TableField("title")
    private String title;

    @TableField("locale")
    private String locale;

    @TableField("status")
    private String status;

    @TableField("context_summary")
    private String contextSummary;

    @TableField("total_turn_count")
    private Integer totalTurnCount;

    @TableField("total_tokens_used")
    private Integer totalTokensUsed;

    @TableField("metadata")
    private String metadata;

    @TableField("created_at")
    private Instant createdAt;

    @TableField("updated_at")
    private Instant updatedAt;

    @TableField("deleted_at")
    private Instant deletedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getApplicationId() { return applicationId; }
    public void setApplicationId(UUID applicationId) { this.applicationId = applicationId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getLocale() { return locale; }
    public void setLocale(String locale) { this.locale = locale; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getContextSummary() { return contextSummary; }
    public void setContextSummary(String contextSummary) { this.contextSummary = contextSummary; }

    public Integer getTotalTurnCount() { return totalTurnCount; }
    public void setTotalTurnCount(Integer totalTurnCount) { this.totalTurnCount = totalTurnCount; }

    public Integer getTotalTokensUsed() { return totalTokensUsed; }
    public void setTotalTokensUsed(Integer totalTokensUsed) { this.totalTokensUsed = totalTokensUsed; }

    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }
}

