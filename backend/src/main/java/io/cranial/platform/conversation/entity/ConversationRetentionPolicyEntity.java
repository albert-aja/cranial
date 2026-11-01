package io.cranial.platform.conversation.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;
import java.util.UUID;

@TableName("ai_conversation_retention_policies")
public class ConversationRetentionPolicyEntity {

    @TableId
    private UUID id;

    @TableField("application_id")
    private UUID applicationId;

    @TableField("retention_days")
    private Integer retentionDays;

    @TableField("auto_prune_enabled")
    private Boolean autoPruneEnabled;

    @TableField("export_on_prune")
    private Boolean exportOnPrune;

    @TableField("default_locale")
    private String defaultLocale;

    @TableField("enforce_safety_guard")
    private Boolean enforceSafetyGuard;

    @TableField("max_context_turns")
    private Integer maxContextTurns;

    @TableField("max_context_tokens")
    private Integer maxContextTokens;

    @TableField("created_at")
    private Instant createdAt;

    @TableField("updated_at")
    private Instant updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getApplicationId() { return applicationId; }
    public void setApplicationId(UUID applicationId) { this.applicationId = applicationId; }

    public Integer getRetentionDays() { return retentionDays; }
    public void setRetentionDays(Integer retentionDays) { this.retentionDays = retentionDays; }

    public Boolean getAutoPruneEnabled() { return autoPruneEnabled; }
    public void setAutoPruneEnabled(Boolean autoPruneEnabled) { this.autoPruneEnabled = autoPruneEnabled; }

    public Boolean getExportOnPrune() { return exportOnPrune; }
    public void setExportOnPrune(Boolean exportOnPrune) { this.exportOnPrune = exportOnPrune; }

    public String getDefaultLocale() { return defaultLocale; }
    public void setDefaultLocale(String defaultLocale) { this.defaultLocale = defaultLocale; }

    public Boolean getEnforceSafetyGuard() { return enforceSafetyGuard; }
    public void setEnforceSafetyGuard(Boolean enforceSafetyGuard) { this.enforceSafetyGuard = enforceSafetyGuard; }

    public Integer getMaxContextTurns() { return maxContextTurns; }
    public void setMaxContextTurns(Integer maxContextTurns) { this.maxContextTurns = maxContextTurns; }

    public Integer getMaxContextTokens() { return maxContextTokens; }
    public void setMaxContextTokens(Integer maxContextTokens) { this.maxContextTokens = maxContextTokens; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

