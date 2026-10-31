package io.cranial.platform.provider.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;
import java.util.UUID;

@TableName("ai_models")
public class ModelEntity {

    @TableId(type = IdType.INPUT)
    private UUID id;
    private UUID providerId;
    private String modelIdentifier;
    private String displayName;
    private String modality; // CHAT, EMBEDDING, VISION, MULTIMODAL
    private Integer contextWindowTokens;
    private Integer maxOutputTokens;
    private Integer costPerMillionInputCents;
    private Integer costPerMillionOutputCents;
    private Boolean isLocal;
    private String status; // ACTIVE, DISABLED
    private String metadataJson;
    private Instant createdAt;
    private Instant updatedAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getProviderId() {
        return providerId;
    }

    public void setProviderId(UUID providerId) {
        this.providerId = providerId;
    }

    public String getModelIdentifier() {
        return modelIdentifier;
    }

    public void setModelIdentifier(String modelIdentifier) {
        this.modelIdentifier = modelIdentifier;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getModality() {
        return modality;
    }

    public void setModality(String modality) {
        this.modality = modality;
    }

    public Integer getContextWindowTokens() {
        return contextWindowTokens;
    }

    public void setContextWindowTokens(Integer contextWindowTokens) {
        this.contextWindowTokens = contextWindowTokens;
    }

    public Integer getMaxOutputTokens() {
        return maxOutputTokens;
    }

    public void setMaxOutputTokens(Integer maxOutputTokens) {
        this.maxOutputTokens = maxOutputTokens;
    }

    public Integer getCostPerMillionInputCents() {
        return costPerMillionInputCents;
    }

    public void setCostPerMillionInputCents(Integer costPerMillionInputCents) {
        this.costPerMillionInputCents = costPerMillionInputCents;
    }

    public Integer getCostPerMillionOutputCents() {
        return costPerMillionOutputCents;
    }

    public void setCostPerMillionOutputCents(Integer costPerMillionOutputCents) {
        this.costPerMillionOutputCents = costPerMillionOutputCents;
    }

    public Boolean getIsLocal() {
        return isLocal;
    }

    public void setIsLocal(Boolean isLocal) {
        this.isLocal = isLocal;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMetadataJson() {
        return metadataJson;
    }

    public void setMetadataJson(String metadataJson) {
        this.metadataJson = metadataJson;
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

