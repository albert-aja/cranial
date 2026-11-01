package io.cranial.platform.conversation.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;
import java.util.UUID;

@TableName("ai_conversation_messages")
public class ConversationMessageEntity {

    @TableId
    private UUID id;

    @TableField("conversation_id")
    private UUID conversationId;

    @TableField("role")
    private String role;

    @TableField("content")
    private String content;

    @TableField("raw_content")
    private String rawContent;

    @TableField("token_count")
    private Integer tokenCount;

    @TableField("structured_blocks")
    private String structuredBlocks;

    @TableField("source_references")
    private String sourceReferences;

    @TableField("tools_invoked")
    private String toolsInvoked;

    @TableField("model_used")
    private String modelUsed;

    @TableField("latency_ms")
    private Long latencyMs;

    @TableField("is_safety_violation")
    private Boolean isSafetyViolation;

    @TableField("safety_reason")
    private String safetyReason;

    @TableField("created_at")
    private Instant createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getConversationId() { return conversationId; }
    public void setConversationId(UUID conversationId) { this.conversationId = conversationId; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getRawContent() { return rawContent; }
    public void setRawContent(String rawContent) { this.rawContent = rawContent; }

    public Integer getTokenCount() { return tokenCount; }
    public void setTokenCount(Integer tokenCount) { this.tokenCount = tokenCount; }

    public String getStructuredBlocks() { return structuredBlocks; }
    public void setStructuredBlocks(String structuredBlocks) { this.structuredBlocks = structuredBlocks; }

    public String getSourceReferences() { return sourceReferences; }
    public void setSourceReferences(String sourceReferences) { this.sourceReferences = sourceReferences; }

    public String getToolsInvoked() { return toolsInvoked; }
    public void setToolsInvoked(String toolsInvoked) { this.toolsInvoked = toolsInvoked; }

    public String getModelUsed() { return modelUsed; }
    public void setModelUsed(String modelUsed) { this.modelUsed = modelUsed; }

    public Long getLatencyMs() { return latencyMs; }
    public void setLatencyMs(Long latencyMs) { this.latencyMs = latencyMs; }

    public Boolean getIsSafetyViolation() { return isSafetyViolation; }
    public void setIsSafetyViolation(Boolean safetyViolation) { isSafetyViolation = safetyViolation; }

    public String getSafetyReason() { return safetyReason; }
    public void setSafetyReason(String safetyReason) { this.safetyReason = safetyReason; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}

