package io.cranial.platform.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;
import java.util.UUID;

@TableName("ai_knowledge_source_events")
public class KnowledgeSourceEventEntity {

    @TableId(type = IdType.INPUT)
    private UUID id;
    private UUID collectionId;
    private UUID sourceId;
    private String sourceCode;
    private String eventType;
    private Integer sourceVersion;
    private String previousSha256;
    private String newSha256;
    private Integer chunksAdded;
    private Integer chunksReused;
    private Integer chunksRemoved;
    private Long durationMs;
    private String actor;
    private String detailsJson;
    private Instant createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getCollectionId() { return collectionId; }
    public void setCollectionId(UUID collectionId) { this.collectionId = collectionId; }

    public UUID getSourceId() { return sourceId; }
    public void setSourceId(UUID sourceId) { this.sourceId = sourceId; }

    public String getSourceCode() { return sourceCode; }
    public void setSourceCode(String sourceCode) { this.sourceCode = sourceCode; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public Integer getSourceVersion() { return sourceVersion; }
    public void setSourceVersion(Integer sourceVersion) { this.sourceVersion = sourceVersion; }

    public String getPreviousSha256() { return previousSha256; }
    public void setPreviousSha256(String previousSha256) { this.previousSha256 = previousSha256; }

    public String getNewSha256() { return newSha256; }
    public void setNewSha256(String newSha256) { this.newSha256 = newSha256; }

    public Integer getChunksAdded() { return chunksAdded; }
    public void setChunksAdded(Integer chunksAdded) { this.chunksAdded = chunksAdded; }

    public Integer getChunksReused() { return chunksReused; }
    public void setChunksReused(Integer chunksReused) { this.chunksReused = chunksReused; }

    public Integer getChunksRemoved() { return chunksRemoved; }
    public void setChunksRemoved(Integer chunksRemoved) { this.chunksRemoved = chunksRemoved; }

    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }

    public String getActor() { return actor; }
    public void setActor(String actor) { this.actor = actor; }

    public String getDetailsJson() { return detailsJson; }
    public void setDetailsJson(String detailsJson) { this.detailsJson = detailsJson; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}

