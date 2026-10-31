package io.cranial.platform.analytics.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;
import java.util.UUID;

@TableName("ai_analytics_query_cache")
public class AnalyticsQueryCacheEntity {

    @TableId
    private UUID id;

    @TableField("query_hash")
    private String queryHash;

    @TableField("application_id")
    private UUID applicationId;

    @TableField("dataset_code")
    private String datasetCode;

    @TableField("query_payload_json")
    private String queryPayloadJson;

    @TableField("result_payload_json")
    private String resultPayloadJson;

    @TableField("hit_count")
    private Integer hitCount;

    @TableField("expires_at")
    private Instant expiresAt;

    @TableField("created_at")
    private Instant createdAt;

    @TableField("last_accessed_at")
    private Instant lastAccessedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getQueryHash() { return queryHash; }
    public void setQueryHash(String queryHash) { this.queryHash = queryHash; }

    public UUID getApplicationId() { return applicationId; }
    public void setApplicationId(UUID applicationId) { this.applicationId = applicationId; }

    public String getDatasetCode() { return datasetCode; }
    public void setDatasetCode(String datasetCode) { this.datasetCode = datasetCode; }

    public String getQueryPayloadJson() { return queryPayloadJson; }
    public void setQueryPayloadJson(String queryPayloadJson) { this.queryPayloadJson = queryPayloadJson; }

    public String getResultPayloadJson() { return resultPayloadJson; }
    public void setResultPayloadJson(String resultPayloadJson) { this.resultPayloadJson = resultPayloadJson; }

    public Integer getHitCount() { return hitCount; }
    public void setHitCount(Integer hitCount) { this.hitCount = hitCount; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getLastAccessedAt() { return lastAccessedAt; }
    public void setLastAccessedAt(Instant lastAccessedAt) { this.lastAccessedAt = lastAccessedAt; }
}

