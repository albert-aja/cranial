package io.cranial.platform.orchestration.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;
import java.util.UUID;

@TableName("ai_task_definitions")
public class TaskDefinitionEntity {

    @TableId(type = IdType.INPUT)
    private UUID id;
    private String taskCode;
    private String name;
    private String description;
    private String capabilityCode;
    private UUID defaultPromptId;
    private UUID outputSchemaId;
    private Integer timeoutSeconds;
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

    public String getTaskCode() {
        return taskCode;
    }

    public void setTaskCode(String taskCode) {
        this.taskCode = taskCode;
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

    public String getCapabilityCode() {
        return capabilityCode;
    }

    public void setCapabilityCode(String capabilityCode) {
        this.capabilityCode = capabilityCode;
    }

    public UUID getDefaultPromptId() {
        return defaultPromptId;
    }

    public void setDefaultPromptId(UUID defaultPromptId) {
        this.defaultPromptId = defaultPromptId;
    }

    public UUID getOutputSchemaId() {
        return outputSchemaId;
    }

    public void setOutputSchemaId(UUID outputSchemaId) {
        this.outputSchemaId = outputSchemaId;
    }

    public Integer getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(Integer timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
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

