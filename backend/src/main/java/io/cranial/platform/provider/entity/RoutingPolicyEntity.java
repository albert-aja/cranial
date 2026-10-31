package io.cranial.platform.provider.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;
import java.util.UUID;

@TableName("ai_routing_policies")
public class RoutingPolicyEntity {

    @TableId(type = IdType.INPUT)
    private UUID id;
    private String name;
    private String capabilityCode;
    private String taskCode;
    private UUID preferredModelId;
    private UUID fallbackModelId;
    private String allowedDataSensitivity; // PUBLIC, INTERNAL, RESTRICTED
    private Boolean isActive;
    private Instant createdAt;
    private Instant updatedAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCapabilityCode() {
        return capabilityCode;
    }

    public void setCapabilityCode(String capabilityCode) {
        this.capabilityCode = capabilityCode;
    }

    public String getTaskCode() {
        return taskCode;
    }

    public void setTaskCode(String taskCode) {
        this.taskCode = taskCode;
    }

    public UUID getPreferredModelId() {
        return preferredModelId;
    }

    public void setPreferredModelId(UUID preferredModelId) {
        this.preferredModelId = preferredModelId;
    }

    public UUID getFallbackModelId() {
        return fallbackModelId;
    }

    public void setFallbackModelId(UUID fallbackModelId) {
        this.fallbackModelId = fallbackModelId;
    }

    public String getAllowedDataSensitivity() {
        return allowedDataSensitivity;
    }

    public void setAllowedDataSensitivity(String allowedDataSensitivity) {
        this.allowedDataSensitivity = allowedDataSensitivity;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
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

