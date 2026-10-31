package io.cranial.platform.decision.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;
import java.util.UUID;

@TableName("ai_decision_plans")
public class DecisionPlanEntity {

    @TableId
    private UUID id;

    @TableField("plan_code")
    private String planCode;

    @TableField("application_id")
    private UUID applicationId;

    @TableField("tenant_id")
    private UUID tenantId;

    @TableField("name")
    private String name;

    @TableField("domain_archetype")
    private String domainArchetype;

    @TableField("description")
    private String description;

    @TableField("parameters_json")
    private String parametersJson;

    @TableField("constraints_json")
    private String constraintsJson;

    @TableField("freshness_ttl_hours")
    private Integer freshnessTtlHours;

    @TableField("is_active")
    private Boolean isActive;

    @TableField("created_at")
    private Instant createdAt;

    @TableField("updated_at")
    private Instant updatedAt;

    public DecisionPlanEntity() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getPlanCode() {
        return planCode;
    }

    public void setPlanCode(String planCode) {
        this.planCode = planCode;
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDomainArchetype() {
        return domainArchetype;
    }

    public void setDomainArchetype(String domainArchetype) {
        this.domainArchetype = domainArchetype;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getParametersJson() {
        return parametersJson;
    }

    public void setParametersJson(String parametersJson) {
        this.parametersJson = parametersJson;
    }

    public String getConstraintsJson() {
        return constraintsJson;
    }

    public void setConstraintsJson(String constraintsJson) {
        this.constraintsJson = constraintsJson;
    }

    public Integer getFreshnessTtlHours() {
        return freshnessTtlHours;
    }

    public void setFreshnessTtlHours(Integer freshnessTtlHours) {
        this.freshnessTtlHours = freshnessTtlHours;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean active) {
        isActive = active;
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

