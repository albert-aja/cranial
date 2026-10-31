package io.cranial.platform.analytics.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@TableName("ai_analytics_anomalies")
public class AnalyticsAnomalyEntity {

    @TableId
    private UUID id;

    @TableField("dataset_id")
    private UUID datasetId;

    @TableField("application_id")
    private UUID applicationId;

    @TableField("tenant_id")
    private UUID tenantId;

    @TableField("metric_name")
    private String metricName;

    @TableField("period_timestamp")
    private Instant periodTimestamp;

    @TableField("actual_value")
    private BigDecimal actualValue;

    @TableField("expected_baseline")
    private BigDecimal expectedBaseline;

    @TableField("deviation_score")
    private BigDecimal deviationScore;

    @TableField("direction")
    private String direction;

    @TableField("severity")
    private String severity;

    @TableField("algorithm")
    private String algorithm;

    @TableField("status")
    private String status;

    @TableField("review_notes")
    private String reviewNotes;

    @TableField("reviewed_by")
    private String reviewedBy;

    @TableField("reviewed_at")
    private Instant reviewedAt;

    @TableField("details_json")
    private String detailsJson;

    @TableField("created_at")
    private Instant createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getDatasetId() { return datasetId; }
    public void setDatasetId(UUID datasetId) { this.datasetId = datasetId; }

    public UUID getApplicationId() { return applicationId; }
    public void setApplicationId(UUID applicationId) { this.applicationId = applicationId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getMetricName() { return metricName; }
    public void setMetricName(String metricName) { this.metricName = metricName; }

    public Instant getPeriodTimestamp() { return periodTimestamp; }
    public void setPeriodTimestamp(Instant periodTimestamp) { this.periodTimestamp = periodTimestamp; }

    public BigDecimal getActualValue() { return actualValue; }
    public void setActualValue(BigDecimal actualValue) { this.actualValue = actualValue; }

    public BigDecimal getExpectedBaseline() { return expectedBaseline; }
    public void setExpectedBaseline(BigDecimal expectedBaseline) { this.expectedBaseline = expectedBaseline; }

    public BigDecimal getDeviationScore() { return deviationScore; }
    public void setDeviationScore(BigDecimal deviationScore) { this.deviationScore = deviationScore; }

    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public String getAlgorithm() { return algorithm; }
    public void setAlgorithm(String algorithm) { this.algorithm = algorithm; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getReviewNotes() { return reviewNotes; }
    public void setReviewNotes(String reviewNotes) { this.reviewNotes = reviewNotes; }

    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }

    public Instant getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Instant reviewedAt) { this.reviewedAt = reviewedAt; }

    public String getDetailsJson() { return detailsJson; }
    public void setDetailsJson(String detailsJson) { this.detailsJson = detailsJson; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}

