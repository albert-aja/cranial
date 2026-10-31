package io.cranial.platform.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;
import java.util.UUID;

@TableName("ai_knowledge_chunks")
public class KnowledgeChunkEntity {

    @TableId(type = IdType.INPUT)
    private UUID id;
    private UUID collectionId;
    private UUID sourceId;
    private UUID applicationId;
    private UUID tenantId;
    private Integer sourceVersion;
    private Integer chunkIndex;
    private String chunkSha256;
    private String content;
    private String parentSectionContent;
    private String normalizedTerms;
    private Integer tokenCount;
    private Integer pageNumber;
    private String sectionTitle;
    private Integer charStart;
    private Integer charEnd;
    private String embeddingModelCode;
    private Integer embeddingDimensions;
    private String embeddingJson;
    private String metadataJson;
    private Instant expiresAt;
    private Instant createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getCollectionId() { return collectionId; }
    public void setCollectionId(UUID collectionId) { this.collectionId = collectionId; }

    public UUID getSourceId() { return sourceId; }
    public void setSourceId(UUID sourceId) { this.sourceId = sourceId; }

    public UUID getApplicationId() { return applicationId; }
    public void setApplicationId(UUID applicationId) { this.applicationId = applicationId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public Integer getSourceVersion() { return sourceVersion; }
    public void setSourceVersion(Integer sourceVersion) { this.sourceVersion = sourceVersion; }

    public Integer getChunkIndex() { return chunkIndex; }
    public void setChunkIndex(Integer chunkIndex) { this.chunkIndex = chunkIndex; }

    public String getChunkSha256() { return chunkSha256; }
    public void setChunkSha256(String chunkSha256) { this.chunkSha256 = chunkSha256; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getParentSectionContent() { return parentSectionContent; }
    public void setParentSectionContent(String parentSectionContent) { this.parentSectionContent = parentSectionContent; }

    public String getNormalizedTerms() { return normalizedTerms; }
    public void setNormalizedTerms(String normalizedTerms) { this.normalizedTerms = normalizedTerms; }

    public Integer getTokenCount() { return tokenCount; }
    public void setTokenCount(Integer tokenCount) { this.tokenCount = tokenCount; }

    public Integer getPageNumber() { return pageNumber; }
    public void setPageNumber(Integer pageNumber) { this.pageNumber = pageNumber; }

    public String getSectionTitle() { return sectionTitle; }
    public void setSectionTitle(String sectionTitle) { this.sectionTitle = sectionTitle; }

    public Integer getCharStart() { return charStart; }
    public void setCharStart(Integer charStart) { this.charStart = charStart; }

    public Integer getCharEnd() { return charEnd; }
    public void setCharEnd(Integer charEnd) { this.charEnd = charEnd; }

    public String getEmbeddingModelCode() { return embeddingModelCode; }
    public void setEmbeddingModelCode(String embeddingModelCode) { this.embeddingModelCode = embeddingModelCode; }

    public Integer getEmbeddingDimensions() { return embeddingDimensions; }
    public void setEmbeddingDimensions(Integer embeddingDimensions) { this.embeddingDimensions = embeddingDimensions; }

    public String getEmbeddingJson() { return embeddingJson; }
    public void setEmbeddingJson(String embeddingJson) { this.embeddingJson = embeddingJson; }

    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}

