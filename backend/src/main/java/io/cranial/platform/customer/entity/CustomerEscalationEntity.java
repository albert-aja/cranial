package io.cranial.platform.customer.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;
import java.util.UUID;

@TableName("ai_customer_escalations")
public class CustomerEscalationEntity {

    @TableId
    private UUID id;

    @TableField("ticket_id")
    private UUID ticketId;

    @TableField("application_id")
    private UUID applicationId;

    @TableField("tenant_id")
    private UUID tenantId;

    @TableField("escalation_reason")
    private String escalationReason;

    @TableField("executive_summary")
    private String executiveSummary;

    @TableField("customer_sentiment_summary")
    private String customerSentimentSummary;

    @TableField("attempts_made")
    private String attemptsMade;

    @TableField("recommended_actions_json")
    private String recommendedActionsJson;

    @TableField("status")
    private String status;

    @TableField("assigned_agent")
    private String assignedAgent;

    @TableField("resolution_notes")
    private String resolutionNotes;

    @TableField("resolved_at")
    private Instant resolvedAt;

    @TableField("created_at")
    private Instant createdAt;

    @TableField("updated_at")
    private Instant updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getTicketId() { return ticketId; }
    public void setTicketId(UUID ticketId) { this.ticketId = ticketId; }

    public UUID getApplicationId() { return applicationId; }
    public void setApplicationId(UUID applicationId) { this.applicationId = applicationId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getEscalationReason() { return escalationReason; }
    public void setEscalationReason(String escalationReason) { this.escalationReason = escalationReason; }

    public String getExecutiveSummary() { return executiveSummary; }
    public void setExecutiveSummary(String executiveSummary) { this.executiveSummary = executiveSummary; }

    public String getCustomerSentimentSummary() { return customerSentimentSummary; }
    public void setCustomerSentimentSummary(String customerSentimentSummary) { this.customerSentimentSummary = customerSentimentSummary; }

    public String getAttemptsMade() { return attemptsMade; }
    public void setAttemptsMade(String attemptsMade) { this.attemptsMade = attemptsMade; }

    public String getRecommendedActionsJson() { return recommendedActionsJson; }
    public void setRecommendedActionsJson(String recommendedActionsJson) { this.recommendedActionsJson = recommendedActionsJson; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getAssignedAgent() { return assignedAgent; }
    public void setAssignedAgent(String assignedAgent) { this.assignedAgent = assignedAgent; }

    public String getResolutionNotes() { return resolutionNotes; }
    public void setResolutionNotes(String resolutionNotes) { this.resolutionNotes = resolutionNotes; }

    public Instant getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

