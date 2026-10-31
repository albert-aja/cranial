package io.cranial.platform.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;
import java.util.UUID;

@TableName("ai_knowledge_feedback")
public class KnowledgeFeedbackEntity {

    @TableId(type = IdType.INPUT)
    private UUID id;
    private UUID collectionId;
    private UUID applicationId;
    private UUID tenantId;
    private String externalUserRef;
    private String questionText;
    private String answerText;
    private String rating;
    private String citedSourceCodesJson;
    private String expectedSourceCode;
    private String comment;
    private UUID promotedEvalCaseId;
    private Instant createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getCollectionId() { return collectionId; }
    public void setCollectionId(UUID collectionId) { this.collectionId = collectionId; }

    public UUID getApplicationId() { return applicationId; }
    public void setApplicationId(UUID applicationId) { this.applicationId = applicationId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getExternalUserRef() { return externalUserRef; }
    public void setExternalUserRef(String externalUserRef) { this.externalUserRef = externalUserRef; }

    public String getQuestionText() { return questionText; }
    public void setQuestionText(String questionText) { this.questionText = questionText; }

    public String getAnswerText() { return answerText; }
    public void setAnswerText(String answerText) { this.answerText = answerText; }

    public String getRating() { return rating; }
    public void setRating(String rating) { this.rating = rating; }

    public String getCitedSourceCodesJson() { return citedSourceCodesJson; }
    public void setCitedSourceCodesJson(String citedSourceCodesJson) { this.citedSourceCodesJson = citedSourceCodesJson; }

    public String getExpectedSourceCode() { return expectedSourceCode; }
    public void setExpectedSourceCode(String expectedSourceCode) { this.expectedSourceCode = expectedSourceCode; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }

    public UUID getPromotedEvalCaseId() { return promotedEvalCaseId; }
    public void setPromotedEvalCaseId(UUID promotedEvalCaseId) { this.promotedEvalCaseId = promotedEvalCaseId; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}

