package io.cranial.platform.orchestration.service;

import io.cranial.platform.orchestration.entity.SchemaEntity;
import io.cranial.platform.orchestration.mapper.SchemaMapper;
import io.cranial.platform.orchestration.model.SchemaValidationResult;
import io.cranial.platform.orchestration.model.SchemaView;
import io.cranial.platform.orchestration.schema.SchemaValidator;
import io.cranial.platform.platform.exception.CranialException;
import io.cranial.platform.platform.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class SchemaRegistryService {

    private final SchemaMapper schemaMapper;
    private final SchemaValidator schemaValidator;

    public SchemaRegistryService(SchemaMapper schemaMapper, SchemaValidator schemaValidator) {
        this.schemaMapper = schemaMapper;
        this.schemaValidator = schemaValidator;
    }

    public List<SchemaView> listSchemas() {
        return schemaMapper.selectList(null).stream()
                .map(this::toView)
                .toList();
    }

    public SchemaView getSchema(UUID id) {
        SchemaEntity entity = schemaMapper.selectById(id);
        if (entity == null) {
            throw new CranialException(ErrorCode.SCHEMA_NOT_FOUND, "Schema not found: " + id);
        }
        return toView(entity);
    }

    public SchemaEntity getRequiredEntity(UUID id) {
        SchemaEntity entity = schemaMapper.selectById(id);
        if (entity == null) {
            throw new CranialException(ErrorCode.SCHEMA_NOT_FOUND, "Schema not found: " + id);
        }
        return entity;
    }

    @Transactional
    public SchemaView saveSchema(
            UUID id,
            String schemaCode,
            String name,
            String description,
            String schemaType,
            Integer version,
            String schemaDefinitionJson,
            Boolean isStrict,
            String status
    ) {
        SchemaEntity entity = id != null ? schemaMapper.selectById(id) : null;
        boolean create = false;
        if (entity == null) {
            entity = new SchemaEntity();
            entity.setId(id != null ? id : UUID.randomUUID());
            entity.setCreatedAt(Instant.now());
            create = true;
        }

        entity.setSchemaCode(schemaCode.trim().toLowerCase());
        entity.setName(name.trim());
        entity.setDescription(description);
        entity.setSchemaType(schemaType.toUpperCase());
        entity.setVersion(version != null && version > 0 ? version : 1);
        entity.setSchemaDefinitionJson(schemaDefinitionJson != null ? schemaDefinitionJson : "{}");
        entity.setIsStrict(isStrict == null || isStrict);
        entity.setStatus(status != null ? status.toUpperCase() : "ACTIVE");
        entity.setUpdatedAt(Instant.now());

        if (create) {
            schemaMapper.insert(entity);
        } else {
            schemaMapper.updateById(entity);
        }

        return toView(entity);
    }

    public SchemaValidationResult validateData(UUID schemaId, Object data) {
        SchemaEntity entity = getRequiredEntity(schemaId);
        return schemaValidator.validate(entity.getSchemaDefinitionJson(), data);
    }

    private SchemaView toView(SchemaEntity e) {
        return new SchemaView(
                e.getId(),
                e.getSchemaCode(),
                e.getName(),
                e.getDescription(),
                e.getSchemaType(),
                e.getVersion() != null ? e.getVersion() : 1,
                e.getSchemaDefinitionJson(),
                Boolean.TRUE.equals(e.getIsStrict()),
                e.getStatus(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }
}

