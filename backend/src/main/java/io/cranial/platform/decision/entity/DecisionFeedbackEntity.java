package io.cranial.platform.decision.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;
import java.util.UUID;

@TableName("ai_decision_feedbacks")
public class DecisionFeedbackEntity {

    @TableId
    private UUID id;

    @TableField("recommendation_id")
    private UUID recommendationId;

    @TableField("application_id")
    private UUID applicationId;

    @TableField("rating")
    private String rating;

    @TableField("applied")
    private Boolean applied;

    @TableField("measured_outcome_json")
    private String measuredOutcomeJson;

    @TableField("comments")
    private String comments;

    @TableField("external_user_ref")
    private String externalUserRef;

    @TableField("created_at")
    private Instant createdAt;

    public DecisionFeedbackEntity() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getRecommendationId() {
        return recommendationId;
    }

    public void setRecommendationId(UUID recommendationId) {
        this.recommendationId = recommendationId;
    }

    public UUID getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(UUID applicationId) {
        this.applicationId = applicationId;
    }

    public String getRating() {
        return rating;
    }

    public void setRating(String rating) {
        this.rating = rating;
    }

    public Boolean getApplied() {
        return applied;
    }

    public void setApplied(Boolean applied) {
        this.applied = applied;
    }

    public String getMeasuredOutcomeJson() {
        return measuredOutcomeJson;
    }

    public void setMeasuredOutcomeJson(String measuredOutcomeJson) {
        this.measuredOutcomeJson = measuredOutcomeJson;
    }

    public String getComments() {
        return comments;
    }

    public void setComments(String comments) {
        this.comments = comments;
    }

    public String getExternalUserRef() {
        return externalUserRef;
    }

    public void setExternalUserRef(String externalUserRef) {
        this.externalUserRef = externalUserRef;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}

