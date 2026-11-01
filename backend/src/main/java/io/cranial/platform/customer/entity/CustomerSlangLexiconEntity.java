package io.cranial.platform.customer.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;
import java.util.UUID;

@TableName("ai_customer_slang_lexicon")
public class CustomerSlangLexiconEntity {

    @TableId
    private UUID id;

    @TableField("slang_term")
    private String slangTerm;

    @TableField("canonical_form")
    private String canonicalForm;

    @TableField("category")
    private String category;

    @TableField("locale")
    private String locale;

    @TableField("is_banking_boundary_hint")
    private Boolean isBankingBoundaryHint;

    @TableField("explanation")
    private String explanation;

    @TableField("is_active")
    private Boolean isActive;

    @TableField("created_at")
    private Instant createdAt;

    @TableField("updated_at")
    private Instant updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getSlangTerm() { return slangTerm; }
    public void setSlangTerm(String slangTerm) { this.slangTerm = slangTerm; }

    public String getCanonicalForm() { return canonicalForm; }
    public void setCanonicalForm(String canonicalForm) { this.canonicalForm = canonicalForm; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getLocale() { return locale; }
    public void setLocale(String locale) { this.locale = locale; }

    public Boolean getIsBankingBoundaryHint() { return isBankingBoundaryHint; }
    public void setIsBankingBoundaryHint(Boolean isBankingBoundaryHint) { this.isBankingBoundaryHint = isBankingBoundaryHint; }

    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

