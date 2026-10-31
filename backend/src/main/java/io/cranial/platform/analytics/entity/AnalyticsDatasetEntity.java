package io.cranial.platform.analytics.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;
import java.util.UUID;

@TableName("ai_analytics_datasets")
public class AnalyticsDatasetEntity {

    @TableId
    private UUID id;

    @TableField("dataset_code")
    private String datasetCode;

    @TableField("application_id")
    private UUID applicationId;

    @TableField("tenant_id")
    private UUID tenantId;

    @TableField("name")
    private String name;

    @TableField("description")
    private String description;

    @TableField("domain_archetype")
    private String domainArchetype;

    @TableField("schema_definition_json")
    private String schemaDefinitionJson;

    @TableField("connector_type")
    private String connectorType;

    @TableField("source_uri")
    private String sourceUri;

    @TableField("refresh_policy")
    private String refreshPolicy;

    @TableField("data_points_json")
    private String dataPointsJson;

    @TableField("max_query_window_days")
    private Integer maxQueryWindowDays;

    @TableField("is_active")
    private Boolean isActive;

    @TableField("source_freshness_at")
    private Instant sourceFreshnessAt;

    @TableField("created_at")
    private Instant createdAt;

    @TableField("updated_at")
    private Instant updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getDatasetCode() { return datasetCode; }
    public void setDatasetCode(String datasetCode) { this.datasetCode = datasetCode; }

    public UUID getApplicationId() { return applicationId; }
    public void setApplicationId(UUID applicationId) { this.applicationId = applicationId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getDomainArchetype() { return domainArchetype; }
    public void setDomainArchetype(String domainArchetype) { this.domainArchetype = domainArchetype; }

    public String getSchemaDefinitionJson() { return schemaDefinitionJson; }
    public void setSchemaDefinitionJson(String schemaDefinitionJson) { this.schemaDefinitionJson = schemaDefinitionJson; }

    public String getConnectorType() { return connectorType; }
    public void setConnectorType(String connectorType) { this.connectorType = connectorType; }

    public String getSourceUri() { return sourceUri; }
    public void setSourceUri(String sourceUri) { this.sourceUri = sourceUri; }

    public String getRefreshPolicy() { return refreshPolicy; }
    public void setRefreshPolicy(String refreshPolicy) { this.refreshPolicy = refreshPolicy; }

    public String getDataPointsJson() { return dataPointsJson; }
    public void setDataPointsJson(String dataPointsJson) { this.dataPointsJson = dataPointsJson; }

    public Integer getMaxQueryWindowDays() { return maxQueryWindowDays; }
    public void setMaxQueryWindowDays(Integer maxQueryWindowDays) { this.maxQueryWindowDays = maxQueryWindowDays; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean active) { isActive = active; }

    public Instant getSourceFreshnessAt() { return sourceFreshnessAt; }
    public void setSourceFreshnessAt(Instant sourceFreshnessAt) { this.sourceFreshnessAt = sourceFreshnessAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

