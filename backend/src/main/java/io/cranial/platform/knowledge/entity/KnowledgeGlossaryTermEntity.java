package io.cranial.platform.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;
import java.util.UUID;

@TableName("ai_knowledge_glossary_terms")
public class KnowledgeGlossaryTermEntity {

    @TableId(type = IdType.INPUT)
    private UUID id;
    private UUID collectionId;
    private UUID sourceId;
    private UUID chunkId;
    private UUID applicationId;
    private UUID tenantId;
    private String term;
    private String normalizedTerm;
    private String aliasesJson;
    private String locale;
    private String category;
    private String shortDefinition;
    private String relatedTermsJson;
    private Instant createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getCollectionId() { return collectionId; }
    public void setCollectionId(UUID collectionId) { this.collectionId = collectionId; }

    public UUID getSourceId() { return sourceId; }
    public void setSourceId(UUID sourceId) { this.sourceId = sourceId; }

    public UUID getChunkId() { return chunkId; }
    public void setChunkId(UUID chunkId) { this.chunkId = chunkId; }

    public UUID getApplicationId() { return applicationId; }
    public void setApplicationId(UUID applicationId) { this.applicationId = applicationId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getTerm() { return term; }
    public void setTerm(String term) { this.term = term; }

    public String getNormalizedTerm() { return normalizedTerm; }
    public void setNormalizedTerm(String normalizedTerm) { this.normalizedTerm = normalizedTerm; }

    public String getAliasesJson() { return aliasesJson; }
    public void setAliasesJson(String aliasesJson) { this.aliasesJson = aliasesJson; }

    public String getLocale() { return locale; }
    public void setLocale(String locale) { this.locale = locale; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getShortDefinition() { return shortDefinition; }
    public void setShortDefinition(String shortDefinition) { this.shortDefinition = shortDefinition; }

    public String getRelatedTermsJson() { return relatedTermsJson; }
    public void setRelatedTermsJson(String relatedTermsJson) { this.relatedTermsJson = relatedTermsJson; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}

