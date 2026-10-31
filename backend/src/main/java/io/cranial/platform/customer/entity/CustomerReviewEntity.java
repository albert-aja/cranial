package io.cranial.platform.customer.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;
import java.util.UUID;

@TableName("ai_customer_reviews")
public class CustomerReviewEntity {

    @TableId
    private UUID id;

    @TableField("application_id")
    private UUID applicationId;

    @TableField("tenant_id")
    private UUID tenantId;

    @TableField("source_channel")
    private String sourceChannel;

    @TableField("author_alias")
    private String authorAlias;

    @TableField("rating")
    private Integer rating;

    @TableField("raw_review")
    private String rawReview;

    @TableField("redacted_review")
    private String redactedReview;

    @TableField("sentiment")
    private String sentiment;

    @TableField("sentiment_score")
    private Double sentimentScore;

    @TableField("aspects_json")
    private String aspectsJson;

    @TableField("created_at")
    private Instant createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getApplicationId() { return applicationId; }
    public void setApplicationId(UUID applicationId) { this.applicationId = applicationId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getSourceChannel() { return sourceChannel; }
    public void setSourceChannel(String sourceChannel) { this.sourceChannel = sourceChannel; }

    public String getAuthorAlias() { return authorAlias; }
    public void setAuthorAlias(String authorAlias) { this.authorAlias = authorAlias; }

    public Integer getRating() { return rating; }
    public void setRating(Integer rating) { this.rating = rating; }

    public String getRawReview() { return rawReview; }
    public void setRawReview(String rawReview) { this.rawReview = rawReview; }

    public String getRedactedReview() { return redactedReview; }
    public void setRedactedReview(String redactedReview) { this.redactedReview = redactedReview; }

    public String getSentiment() { return sentiment; }
    public void setSentiment(String sentiment) { this.sentiment = sentiment; }

    public Double getSentimentScore() { return sentimentScore; }
    public void setSentimentScore(Double sentimentScore) { this.sentimentScore = sentimentScore; }

    public String getAspectsJson() { return aspectsJson; }
    public void setAspectsJson(String aspectsJson) { this.aspectsJson = aspectsJson; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}

