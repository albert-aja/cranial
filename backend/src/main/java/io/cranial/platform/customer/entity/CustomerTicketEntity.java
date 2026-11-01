package io.cranial.platform.customer.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;
import java.util.UUID;

@TableName("ai_customer_tickets")
public class CustomerTicketEntity {

    @TableId
    private UUID id;

    @TableField("ticket_number")
    private String ticketNumber;

    @TableField("application_id")
    private UUID applicationId;

    @TableField("tenant_id")
    private UUID tenantId;

    @TableField("external_user_ref")
    private String externalUserRef;

    @TableField("customer_name")
    private String customerName;

    @TableField("subject")
    private String subject;

    @TableField("raw_message")
    private String rawMessage;

    @TableField("redacted_message")
    private String redactedMessage;

    @TableField("normalized_message")
    private String normalizedMessage;

    @TableField("slang_normalized")
    private Boolean slangNormalized;

    @TableField("slang_replacements_json")
    private String slangReplacementsJson;

    @TableField("pii_detected")
    private Boolean piiDetected;

    @TableField("pii_entities_json")
    private String piiEntitiesJson;

    @TableField("locale")
    private String locale;

    @TableField("category")
    private String category;

    @TableField("sentiment")
    private String sentiment;

    @TableField("sentiment_score")
    private Double sentimentScore;

    @TableField("urgency")
    private String urgency;

    @TableField("priority")
    private String priority;

    @TableField("routing_queue")
    private String routingQueue;

    @TableField("status")
    private String status;

    @TableField("promise_guard_flagged")
    private Boolean promiseGuardFlagged;

    @TableField("promise_guard_reasons")
    private String promiseGuardReasons;

    @TableField("suggested_canned_response")
    private String suggestedCannedResponse;

    @TableField("confidence_score")
    private Double confidenceScore;

    @TableField("created_at")
    private Instant createdAt;

    @TableField("updated_at")
    private Instant updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getTicketNumber() { return ticketNumber; }
    public void setTicketNumber(String ticketNumber) { this.ticketNumber = ticketNumber; }

    public UUID getApplicationId() { return applicationId; }
    public void setApplicationId(UUID applicationId) { this.applicationId = applicationId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getExternalUserRef() { return externalUserRef; }
    public void setExternalUserRef(String externalUserRef) { this.externalUserRef = externalUserRef; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getRawMessage() { return rawMessage; }
    public void setRawMessage(String rawMessage) { this.rawMessage = rawMessage; }

    public String getRedactedMessage() { return redactedMessage; }
    public void setRedactedMessage(String redactedMessage) { this.redactedMessage = redactedMessage; }

    public String getNormalizedMessage() { return normalizedMessage; }
    public void setNormalizedMessage(String normalizedMessage) { this.normalizedMessage = normalizedMessage; }

    public Boolean getSlangNormalized() { return slangNormalized; }
    public void setSlangNormalized(Boolean slangNormalized) { this.slangNormalized = slangNormalized; }

    public String getSlangReplacementsJson() { return slangReplacementsJson; }
    public void setSlangReplacementsJson(String slangReplacementsJson) { this.slangReplacementsJson = slangReplacementsJson; }

    public Boolean getPiiDetected() { return piiDetected; }
    public void setPiiDetected(Boolean piiDetected) { this.piiDetected = piiDetected; }

    public String getPiiEntitiesJson() { return piiEntitiesJson; }
    public void setPiiEntitiesJson(String piiEntitiesJson) { this.piiEntitiesJson = piiEntitiesJson; }

    public String getLocale() { return locale; }
    public void setLocale(String locale) { this.locale = locale; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getSentiment() { return sentiment; }
    public void setSentiment(String sentiment) { this.sentiment = sentiment; }

    public Double getSentimentScore() { return sentimentScore; }
    public void setSentimentScore(Double sentimentScore) { this.sentimentScore = sentimentScore; }

    public String getUrgency() { return urgency; }
    public void setUrgency(String urgency) { this.urgency = urgency; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public String getRoutingQueue() { return routingQueue; }
    public void setRoutingQueue(String routingQueue) { this.routingQueue = routingQueue; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Boolean getPromiseGuardFlagged() { return promiseGuardFlagged; }
    public void setPromiseGuardFlagged(Boolean promiseGuardFlagged) { this.promiseGuardFlagged = promiseGuardFlagged; }

    public String getPromiseGuardReasons() { return promiseGuardReasons; }
    public void setPromiseGuardReasons(String promiseGuardReasons) { this.promiseGuardReasons = promiseGuardReasons; }

    public String getSuggestedCannedResponse() { return suggestedCannedResponse; }
    public void setSuggestedCannedResponse(String suggestedCannedResponse) { this.suggestedCannedResponse = suggestedCannedResponse; }

    public Double getConfidenceScore() { return confidenceScore; }
    public void setConfidenceScore(Double confidenceScore) { this.confidenceScore = confidenceScore; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

