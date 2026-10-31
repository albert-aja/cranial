package io.cranial.platform.orchestration.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;
import java.util.UUID;

@TableName("ai_schemas")
public class SchemaEntity {

    @TableId(type = IdType.INPUT)
    private UUID id;
    private String schemaCode;
    private String name;
    private String description;
    private String schemaType; // REQUEST, RESPONSE_EXTRACTION, TOOL_INPUT, TOOL_OUTPUT
    private Integer version;
    private String schemaDefinitionJson;
    private Boolean isStrict;
    private String status; // ACTIVE, DEPRECATED
    private Instant createdAt;
    private Instant updatedAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getSchemaCode() {
        return schemaCode;
    }

    public void setSchemaCode(String schemaCode) {
        this.schemaCode = schemaCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSchemaType() {
        return schemaType;
    }

    public void setSchemaType(String schemaType) {
        this.schemaType = schemaType;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public String getSchemaDefinitionJson() {
        return schemaDefinitionJson;
    }

    public void setSchemaDefinitionJson(String schemaDefinitionJson) {
        this.schemaDefinitionJson = schemaDefinitionJson;
    }

    public Boolean getIsStrict() {
        return isStrict;
    }

    public void setIsStrict(Boolean isStrict) {
        this.isStrict = isStrict;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}

