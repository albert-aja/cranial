package io.cranial.platform.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;
import java.util.UUID;

@TableName("ai_knowledge_synonyms")
public class KnowledgeSynonymEntity {

    @TableId(type = IdType.INPUT)
    private UUID id;
    private UUID collectionId;
    private UUID applicationId;
    private String canonicalTerm;
    private String synonymsJson;
    private String localePair;
    private String domainTag;
    private Boolean isActive;
    private Instant createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getCollectionId() { return collectionId; }
    public void setCollectionId(UUID collectionId) { this.collectionId = collectionId; }

    public UUID getApplicationId() { return applicationId; }
    public void setApplicationId(UUID applicationId) { this.applicationId = applicationId; }

    public String getCanonicalTerm() { return canonicalTerm; }
    public void setCanonicalTerm(String canonicalTerm) { this.canonicalTerm = canonicalTerm; }

    public String getSynonymsJson() { return synonymsJson; }
    public void setSynonymsJson(String synonymsJson) { this.synonymsJson = synonymsJson; }

    public String getLocalePair() { return localePair; }
    public void setLocalePair(String localePair) { this.localePair = localePair; }

    public String getDomainTag() { return domainTag; }
    public void setDomainTag(String domainTag) { this.domainTag = domainTag; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}

