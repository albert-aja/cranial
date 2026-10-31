package io.cranial.platform.application.capability;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;
import java.util.UUID;

@TableName("application_capabilities")
public class ApplicationCapabilityEntity {

    @TableId(type = IdType.INPUT)
    private UUID id;
    private UUID applicationId;
    private UUID capabilityId;
    private Boolean enabled;
    private Integer quotaDailyRequests;
    private String configOverride;
    private UUID updatedBy;
    private Instant createdAt;
    private Instant updatedAt;

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

    public UUID getCapabilityId() {
        return capabilityId;
    }

    public void setCapabilityId(UUID capabilityId) {
        this.capabilityId = capabilityId;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Integer getQuotaDailyRequests() {
        return quotaDailyRequests;
    }

    public void setQuotaDailyRequests(Integer quotaDailyRequests) {
        this.quotaDailyRequests = quotaDailyRequests;
    }

    public String getConfigOverride() {
        return configOverride;
    }

    public void setConfigOverride(String configOverride) {
        this.configOverride = configOverride;
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

