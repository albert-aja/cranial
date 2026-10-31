package io.cranial.platform.application.featureflag;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;
import java.util.UUID;

@TableName("platform_feature_flags")
public class FeatureFlagEntity {

    @TableId(type = IdType.INPUT)
    private UUID id;
    private String flagKey;
    private String name;
    private String description;
    private Boolean enabled;
    private Integer rolloutPercentage;
    private String allowedAppCodes;
    private UUID updatedBy;
    private Instant createdAt;
    private Instant updatedAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getFlagKey() {
        return flagKey;
    }

    public void setFlagKey(String flagKey) {
        this.flagKey = flagKey;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Integer getRolloutPercentage() {
        return rolloutPercentage;
    }

    public void setRolloutPercentage(Integer rolloutPercentage) {
        this.rolloutPercentage = rolloutPercentage;
    }

    public String getAllowedAppCodes() {
        return allowedAppCodes;
    }

    public void setAllowedAppCodes(String allowedAppCodes) {
        this.allowedAppCodes = allowedAppCodes;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(UUID updatedBy) {
        this.updatedBy = updatedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}

