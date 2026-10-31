package io.cranial.platform.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;
import java.util.UUID;

@TableName("ai_knowledge_eval_cases")
public class KnowledgeEvalCaseEntity {

    @TableId(type = IdType.INPUT)
    private UUID id;
    private String caseCode;
    private String name;
    private String collectionCode;
    private String queryText;
    private String retrievalMode;
    private Boolean rerank;
    private String simulatedRolesJson;
    private String metadataFiltersJson;
    private String expectedSourceCode;
    private String expectedSectionContains;
    private String expectPermissionBlockedSource;
    private Integer topK;
    private Boolean isActive;
    private Instant createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getCaseCode() { return caseCode; }
    public void setCaseCode(String caseCode) { this.caseCode = caseCode; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCollectionCode() { return collectionCode; }
    public void setCollectionCode(String collectionCode) { this.collectionCode = collectionCode; }

    public String getQueryText() { return queryText; }
    public void setQueryText(String queryText) { this.queryText = queryText; }

    public String getRetrievalMode() { return retrievalMode; }
    public void setRetrievalMode(String retrievalMode) { this.retrievalMode = retrievalMode; }

    public Boolean getRerank() { return rerank; }
    public void setRerank(Boolean rerank) { this.rerank = rerank; }

    public String getSimulatedRolesJson() { return simulatedRolesJson; }
    public void setSimulatedRolesJson(String simulatedRolesJson) { this.simulatedRolesJson = simulatedRolesJson; }

    public String getMetadataFiltersJson() { return metadataFiltersJson; }
    public void setMetadataFiltersJson(String metadataFiltersJson) { this.metadataFiltersJson = metadataFiltersJson; }

    public String getExpectedSourceCode() { return expectedSourceCode; }
    public void setExpectedSourceCode(String expectedSourceCode) { this.expectedSourceCode = expectedSourceCode; }

    public String getExpectedSectionContains() { return expectedSectionContains; }
    public void setExpectedSectionContains(String expectedSectionContains) { this.expectedSectionContains = expectedSectionContains; }

    public String getExpectPermissionBlockedSource() { return expectPermissionBlockedSource; }
    public void setExpectPermissionBlockedSource(String expectPermissionBlockedSource) { this.expectPermissionBlockedSource = expectPermissionBlockedSource; }

    public Integer getTopK() { return topK; }
    public void setTopK(Integer topK) { this.topK = topK; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}

