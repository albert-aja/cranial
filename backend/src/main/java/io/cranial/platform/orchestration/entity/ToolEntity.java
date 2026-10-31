package io.cranial.platform.orchestration.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;
import java.util.UUID;

@TableName("ai_tools")
public class ToolEntity {

    @TableId(type = IdType.INPUT)
    private UUID id;
    private String toolCode;
    private String name;
    private String description;
    private UUID applicationId;
    private UUID inputSchemaId;
    private UUID outputSchemaId;
    private String executionType; // INTERNAL_MOCK, HTTP_WEBHOOK, GATEWAY_CALLBACK
    private String endpointUrl;
    private Boolean isReadOnly;
    private String requiredPermission;
    private String riskLevel; // LOW, MEDIUM, HIGH
    private Integer timeoutMs;
    private String status; // ACTIVE, DISABLED
    private Instant createdAt;
    private Instant updatedAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getToolCode() {
        return toolCode;
    }

    public void setToolCode(String toolCode) {
        this.toolCode = toolCode;
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

    public UUID getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(UUID applicationId) {
        this.applicationId = applicationId;
    }

    public UUID getInputSchemaId() {
        return inputSchemaId;
    }

    public void setInputSchemaId(UUID inputSchemaId) {
        this.inputSchemaId = inputSchemaId;
    }

    public UUID getOutputSchemaId() {
        return outputSchemaId;
    }

    public void setOutputSchemaId(UUID outputSchemaId) {
        this.outputSchemaId = outputSchemaId;
    }

    public String getExecutionType() {
        return executionType;
    }

    public void setExecutionType(String executionType) {
        this.executionType = executionType;
    }

    public String getEndpointUrl() {
        return endpointUrl;
    }

    public void setEndpointUrl(String endpointUrl) {
        this.endpointUrl = endpointUrl;
    }

    public Boolean getIsReadOnly() {
        return isReadOnly;
    }

    public void setIsReadOnly(Boolean isReadOnly) {
        this.isReadOnly = isReadOnly;
    }

    public String getRequiredPermission() {
        return requiredPermission;
    }

    public void setRequiredPermission(String requiredPermission) {
        this.requiredPermission = requiredPermission;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public Integer getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(Integer timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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

