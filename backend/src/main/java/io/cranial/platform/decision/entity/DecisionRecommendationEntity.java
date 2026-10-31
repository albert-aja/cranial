package io.cranial.platform.decision.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@TableName("ai_decision_recommendations")
public class DecisionRecommendationEntity {

    @TableId
    private UUID id;

    @TableField("plan_id")
    private UUID planId;

    @TableField("application_id")
    private UUID applicationId;

    @TableField("tenant_id")
    private UUID tenantId;

    @TableField("recommendation_code")
    private String recommendationCode;

    @TableField("title")
    private String title;

    @TableField("summary")
    private String summary;

    @TableField("confidence_score")
    private BigDecimal confidenceScore;

    @TableField("abstained")
    private Boolean abstained;

    @TableField("abstention_reason")
    private String abstentionReason;

    @TableField("simulation_mode")
    private Boolean simulationMode;

    @TableField("status")
    private String status;

    @TableField("action_type")
    private String actionType;

    @TableField("evidence_rationale_json")
    private String evidenceRationaleJson;

    @TableField("trade_offs_json")
    private String tradeOffsJson;

    @TableField("scenario_diff_json")
    private String scenarioDiffJson;

    @TableField("constraints_validated_json")
    private String constraintsValidatedJson;

    @TableField("reviewed_by")
    private String reviewedBy;

    @TableField("reviewed_at")
    private Instant reviewedAt;

    @TableField("review_notes")
    private String reviewNotes;

    @TableField("source_freshness_at")
    private Instant sourceFreshnessAt;

    @TableField("created_at")
    private Instant createdAt;

    public DecisionRecommendationEntity() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getPlanId() {
        return planId;
    }

    public void setPlanId(UUID planId) {
        this.planId = planId;
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

    public String getRecommendationCode() {
        return recommendationCode;
    }

    public void setRecommendationCode(String recommendationCode) {
        this.recommendationCode = recommendationCode;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public BigDecimal getConfidenceScore() {
        return confidenceScore;
    }

    public void setConfidenceScore(BigDecimal confidenceScore) {
        this.confidenceScore = confidenceScore;
    }

    public Boolean getAbstained() {
        return abstained;
    }

    public void setAbstained(Boolean abstained) {
        this.abstained = abstained;
    }

    public String getAbstentionReason() {
        return abstentionReason;
    }

    public void setAbstentionReason(String abstentionReason) {
        this.abstentionReason = abstentionReason;
    }

    public Boolean getSimulationMode() {
        return simulationMode;
    }

    public void setSimulationMode(Boolean simulationMode) {
        this.simulationMode = simulationMode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getActionType() {
        return actionType;
    }

    public void setActionType(String actionType) {
        this.actionType = actionType;
    }

    public String getEvidenceRationaleJson() {
        return evidenceRationaleJson;
    }

    public void setEvidenceRationaleJson(String evidenceRationaleJson) {
        this.evidenceRationaleJson = evidenceRationaleJson;
    }

    public String getTradeOffsJson() {
        return tradeOffsJson;
    }

    public void setTradeOffsJson(String tradeOffsJson) {
        this.tradeOffsJson = tradeOffsJson;
    }

    public String getScenarioDiffJson() {
        return scenarioDiffJson;
    }

    public void setScenarioDiffJson(String scenarioDiffJson) {
        this.scenarioDiffJson = scenarioDiffJson;
    }

    public String getConstraintsValidatedJson() {
        return constraintsValidatedJson;
    }

    public void setConstraintsValidatedJson(String constraintsValidatedJson) {
        this.constraintsValidatedJson = constraintsValidatedJson;
    }

    public String getReviewedBy() {
        return reviewedBy;
    }

    public void setReviewedBy(String reviewedBy) {
        this.reviewedBy = reviewedBy;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(Instant reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public String getReviewNotes() {
        return reviewNotes;
    }

    public void setReviewNotes(String reviewNotes) {
        this.reviewNotes = reviewNotes;
    }

    public Instant getSourceFreshnessAt() {
        return sourceFreshnessAt;
    }

    public void setSourceFreshnessAt(Instant sourceFreshnessAt) {
        this.sourceFreshnessAt = sourceFreshnessAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}

