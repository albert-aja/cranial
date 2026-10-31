package io.cranial.platform.orchestration.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;
import java.util.UUID;

@TableName("ai_orchestrator_traces")
public class OrchestratorTraceEntity {

    @TableId(type = IdType.INPUT)
    private UUID id;
    private UUID applicationId;
    private UUID tenantId;
    private String correlationId;
    private String taskCode;
    private String capabilityCode;
    private UUID modelId;
    private UUID promptId;
    private UUID schemaId;
    private String status; // SUCCESS, SCHEMA_VALIDATION_FAILED, TOOL_FAILED, ERROR
    private Long totalDurationMs;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer toolInvocationsCount;
    private String stepsJson;
    private String errorMessage;
    private Instant createdAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(UUID applicationId) {
        this.applicationId = applicationId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public String getTaskCode() {
        return taskCode;
    }

    public void setTaskCode(String taskCode) {
        this.taskCode = taskCode;
    }

    public String getCapabilityCode() {
        return capabilityCode;
    }

    public void setCapabilityCode(String capabilityCode) {
        this.capabilityCode = capabilityCode;
    }

    public UUID getModelId() {
        return modelId;
    }

    public void setModelId(UUID modelId) {
        this.modelId = modelId;
    }

    public UUID getPromptId() {
        return promptId;
    }

    public void setPromptId(UUID promptId) {
        this.promptId = promptId;
    }

    public UUID getSchemaId() {
        return schemaId;
    }

    public void setSchemaId(UUID schemaId) {
        this.schemaId = schemaId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getTotalDurationMs() {
        return totalDurationMs;
    }

    public void setTotalDurationMs(Long totalDurationMs) {
        this.totalDurationMs = totalDurationMs;
    }

    public Integer getPromptTokens() {
        return promptTokens;
    }

    public void setPromptTokens(Integer promptTokens) {
        this.promptTokens = promptTokens;
    }

    public Integer getCompletionTokens() {
        return completionTokens;
    }

    public void setCompletionTokens(Integer completionTokens) {
        this.completionTokens = completionTokens;
    }

    public Integer getToolInvocationsCount() {
        return toolInvocationsCount;
    }

    public void setToolInvocationsCount(Integer toolInvocationsCount) {
        this.toolInvocationsCount = toolInvocationsCount;
    }

    public String getStepsJson() {
        return stepsJson;
    }

    public void setStepsJson(String stepsJson) {
        this.stepsJson = stepsJson;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}

