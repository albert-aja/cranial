package io.cranial.platform.orchestration.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;
import java.util.UUID;

@TableName("ai_prompts")
public class PromptEntity {

    @TableId(type = IdType.INPUT)
    private UUID id;
    private String promptCode;
    private String name;
    private String description;
    private String capabilityCode;
    private String taskCode;
    private UUID applicationId;
    private Integer version;
    private String status; // DRAFT, TESTING, PUBLISHED, RETIRED
    private String systemTemplate;
    private String userTemplate;
    private String inputVariablesJson;
    private Instant createdAt;
    private Instant updatedAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getPromptCode() {
        return promptCode;
    }

    public void setPromptCode(String promptCode) {
        this.promptCode = promptCode;
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

    public String getTaskCode() {
        return taskCode;
    }

    public void setTaskCode(String taskCode) {
        this.taskCode = taskCode;
    }

    public UUID getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(UUID applicationId) {
        this.applicationId = applicationId;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSystemTemplate() {
        return systemTemplate;
    }

    public void setSystemTemplate(String systemTemplate) {
        this.systemTemplate = systemTemplate;
    }

    public String getUserTemplate() {
        return userTemplate;
    }

    public void setUserTemplate(String userTemplate) {
        this.userTemplate = userTemplate;
    }

    public String getInputVariablesJson() {
        return inputVariablesJson;
    }

    public void setInputVariablesJson(String inputVariablesJson) {
        this.inputVariablesJson = inputVariablesJson;
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

