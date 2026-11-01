package io.cranial.platform.conversation.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;
import java.util.UUID;

@TableName("ai_conversation_feedbacks")
public class ConversationFeedbackEntity {

    @TableId
    private UUID id;

    @TableField("conversation_id")
    private UUID conversationId;

    @TableField("message_id")
    private UUID messageId;

    @TableField("rating")
    private Integer rating;

    @TableField("is_helpful")
    private Boolean isHelpful;

    @TableField("is_correct")
    private Boolean isCorrect;

    @TableField("feedback_category")
    private String feedbackCategory;

    @TableField("comment")
    private String comment;

    @TableField("operator_id")
    private UUID operatorId;

    @TableField("created_at")
    private Instant createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getConversationId() { return conversationId; }
    public void setConversationId(UUID conversationId) { this.conversationId = conversationId; }

    public UUID getMessageId() { return messageId; }
    public void setMessageId(UUID messageId) { this.messageId = messageId; }

    public Integer getRating() { return rating; }
    public void setRating(Integer rating) { this.rating = rating; }

    public Boolean getIsHelpful() { return isHelpful; }
    public void setIsHelpful(Boolean helpful) { isHelpful = helpful; }

    public Boolean getIsCorrect() { return isCorrect; }
    public void setIsCorrect(Boolean correct) { isCorrect = correct; }

    public String getFeedbackCategory() { return feedbackCategory; }
    public void setFeedbackCategory(String feedbackCategory) { this.feedbackCategory = feedbackCategory; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }

    public UUID getOperatorId() { return operatorId; }
    public void setOperatorId(UUID operatorId) { this.operatorId = operatorId; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}

