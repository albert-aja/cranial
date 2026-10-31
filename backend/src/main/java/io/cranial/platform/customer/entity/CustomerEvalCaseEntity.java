package io.cranial.platform.customer.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;
import java.util.UUID;

@TableName("ai_customer_eval_cases")
public class CustomerEvalCaseEntity {

    @TableId
    private UUID id;

    @TableField("suite_code")
    private String suiteCode;

    @TableField("locale")
    private String locale;

    @TableField("input_text")
    private String inputText;

    @TableField("expected_category")
    private String expectedCategory;

    @TableField("expected_sentiment")
    private String expectedSentiment;

    @TableField("expected_escalation")
    private Boolean expectedEscalation;

    @TableField("expected_promise_violation")
    private Boolean expectedPromiseViolation;

    @TableField("notes")
    private String notes;

    @TableField("created_at")
    private Instant createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getSuiteCode() { return suiteCode; }
    public void setSuiteCode(String suiteCode) { this.suiteCode = suiteCode; }

    public String getLocale() { return locale; }
    public void setLocale(String locale) { this.locale = locale; }

    public String getInputText() { return inputText; }
    public void setInputText(String inputText) { this.inputText = inputText; }

    public String getExpectedCategory() { return expectedCategory; }
    public void setExpectedCategory(String expectedCategory) { this.expectedCategory = expectedCategory; }

    public String getExpectedSentiment() { return expectedSentiment; }
    public void setExpectedSentiment(String expectedSentiment) { this.expectedSentiment = expectedSentiment; }

    public Boolean getExpectedEscalation() { return expectedEscalation; }
    public void setExpectedEscalation(Boolean expectedEscalation) { this.expectedEscalation = expectedEscalation; }

    public Boolean getExpectedPromiseViolation() { return expectedPromiseViolation; }
    public void setExpectedPromiseViolation(Boolean expectedPromiseViolation) { this.expectedPromiseViolation = expectedPromiseViolation; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}

