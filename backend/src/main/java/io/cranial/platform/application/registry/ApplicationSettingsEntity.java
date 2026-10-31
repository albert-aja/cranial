package io.cranial.platform.application.registry;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;
import java.util.UUID;

@TableName("application_settings")
public class ApplicationSettingsEntity {

    @TableId(value = "application_id", type = IdType.INPUT)
    private UUID applicationId;
    private Integer rateLimitRpm;
    private Integer concurrencyLimit;
    private Integer maxInputBytes;
    private Boolean requireSignedContext;
    private Boolean allowHostedProviders;
    private Boolean allowSensitiveExternal;
    private String webhookUrl;
    private String webhookSecretEncrypted;
    private String configJson;
    private Instant updatedAt;

    public UUID getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(UUID applicationId) {
        this.applicationId = applicationId;
    }

    public Integer getRateLimitRpm() {
        return rateLimitRpm;
    }

    public void setRateLimitRpm(Integer rateLimitRpm) {
        this.rateLimitRpm = rateLimitRpm;
    }

    public Integer getConcurrencyLimit() {
        return concurrencyLimit;
    }

    public void setConcurrencyLimit(Integer concurrencyLimit) {
        this.concurrencyLimit = concurrencyLimit;
    }

    public Integer getMaxInputBytes() {
        return maxInputBytes;
    }

    public void setMaxInputBytes(Integer maxInputBytes) {
        this.maxInputBytes = maxInputBytes;
    }

    public Boolean getRequireSignedContext() {
        return requireSignedContext;
    }

    public void setRequireSignedContext(Boolean requireSignedContext) {
        this.requireSignedContext = requireSignedContext;
    }

    public Boolean getAllowHostedProviders() {
        return allowHostedProviders;
    }

    public void setAllowHostedProviders(Boolean allowHostedProviders) {
        this.allowHostedProviders = allowHostedProviders;
    }

    public Boolean getAllowSensitiveExternal() {
        return allowSensitiveExternal;
    }

    public void setAllowSensitiveExternal(Boolean allowSensitiveExternal) {
        this.allowSensitiveExternal = allowSensitiveExternal;
    }

    public String getWebhookUrl() {
        return webhookUrl;
    }

    public void setWebhookUrl(String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    public String getWebhookSecretEncrypted() {
        return webhookSecretEncrypted;
    }

    public void setWebhookSecretEncrypted(String webhookSecretEncrypted) {
        this.webhookSecretEncrypted = webhookSecretEncrypted;
    }

    public String getConfigJson() {
        return configJson;
    }

    public void setConfigJson(String configJson) {
        this.configJson = configJson;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}

