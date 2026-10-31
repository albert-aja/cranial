package io.cranial.platform.provider.entity;

import com.baomidou.mybatisplus.annotation.TableName;

import java.util.UUID;

@TableName("ai_model_capabilities")
public class ModelCapabilityEntity {

    private UUID modelId;
    private String capabilityCode;

    public ModelCapabilityEntity() {
    }

    public ModelCapabilityEntity(UUID modelId, String capabilityCode) {
        this.modelId = modelId;
        this.capabilityCode = capabilityCode;
    }

    public UUID getModelId() {
        return modelId;
    }

    public void setModelId(UUID modelId) {
        this.modelId = modelId;
    }

    public String getCapabilityCode() {
        return capabilityCode;
    }

    public void setCapabilityCode(String capabilityCode) {
        this.capabilityCode = capabilityCode;
    }
}

