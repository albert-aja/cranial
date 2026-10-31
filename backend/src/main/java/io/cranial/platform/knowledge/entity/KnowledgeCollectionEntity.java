package io.cranial.platform.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;
import java.util.UUID;

@TableName("ai_knowledge_collections")
public class KnowledgeCollectionEntity {

    @TableId(type = IdType.INPUT)
    private UUID id;
    private String collectionCode;
    private UUID applicationId;
    private UUID tenantId;
    private String name;
    private String description;
    private String collectionType;
    private String visibilityScope;
    private String defaultChunkStrategy;
    private Integer chunkSizeChars;
    private Integer chunkOverlapChars;
    private String embeddingModelCode;
    private Integer embeddingDimensions;
    private Integer hnswM;
    private Integer hnswEfConstruction;
    private Integer hnswEfSearch;
    private Boolean rerankByDefault;
    private Integer freshnessTtlDays;
    private Integer retentionDays;
    private String allowedRolesJson;
    private String metadataJson;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getCollectionCode() { return collectionCode; }
    public void setCollectionCode(String collectionCode) { this.collectionCode = collectionCode; }

    public UUID getApplicationId() { return applicationId; }
    public void setApplicationId(UUID applicationId) { this.applicationId = applicationId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCollectionType() { return collectionType; }
    public void setCollectionType(String collectionType) { this.collectionType = collectionType; }

    public String getVisibilityScope() { return visibilityScope; }
    public void setVisibilityScope(String visibilityScope) { this.visibilityScope = visibilityScope; }

    public String getDefaultChunkStrategy() { return defaultChunkStrategy; }
    public void setDefaultChunkStrategy(String defaultChunkStrategy) { this.defaultChunkStrategy = defaultChunkStrategy; }

    public Integer getChunkSizeChars() { return chunkSizeChars; }
    public void setChunkSizeChars(Integer chunkSizeChars) { this.chunkSizeChars = chunkSizeChars; }

    public Integer getChunkOverlapChars() { return chunkOverlapChars; }
    public void setChunkOverlapChars(Integer chunkOverlapChars) { this.chunkOverlapChars = chunkOverlapChars; }

    public String getEmbeddingModelCode() { return embeddingModelCode; }
    public void setEmbeddingModelCode(String embeddingModelCode) { this.embeddingModelCode = embeddingModelCode; }

    public Integer getEmbeddingDimensions() { return embeddingDimensions; }
    public void setEmbeddingDimensions(Integer embeddingDimensions) { this.embeddingDimensions = embeddingDimensions; }

    public Integer getHnswM() { return hnswM; }
    public void setHnswM(Integer hnswM) { this.hnswM = hnswM; }

    public Integer getHnswEfConstruction() { return hnswEfConstruction; }
    public void setHnswEfConstruction(Integer hnswEfConstruction) { this.hnswEfConstruction = hnswEfConstruction; }

    public Integer getHnswEfSearch() { return hnswEfSearch; }
    public void setHnswEfSearch(Integer hnswEfSearch) { this.hnswEfSearch = hnswEfSearch; }

    public Boolean getRerankByDefault() { return rerankByDefault; }
    public void setRerankByDefault(Boolean rerankByDefault) { this.rerankByDefault = rerankByDefault; }

    public Integer getFreshnessTtlDays() { return freshnessTtlDays; }
    public void setFreshnessTtlDays(Integer freshnessTtlDays) { this.freshnessTtlDays = freshnessTtlDays; }

    public Integer getRetentionDays() { return retentionDays; }
    public void setRetentionDays(Integer retentionDays) { this.retentionDays = retentionDays; }

    public String getAllowedRolesJson() { return allowedRolesJson; }
    public void setAllowedRolesJson(String allowedRolesJson) { this.allowedRolesJson = allowedRolesJson; }

    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

