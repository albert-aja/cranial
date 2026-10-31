package io.cranial.platform.orchestration.service;

import io.cranial.platform.orchestration.entity.SchemaEntity;
import io.cranial.platform.orchestration.entity.ToolEntity;
import io.cranial.platform.orchestration.mapper.SchemaMapper;
import io.cranial.platform.orchestration.mapper.ToolMapper;
import io.cranial.platform.orchestration.model.ToolExecutionRequest;
import io.cranial.platform.orchestration.model.ToolExecutionResult;
import io.cranial.platform.orchestration.model.ToolView;
import io.cranial.platform.orchestration.tool.ToolExecutor;
import io.cranial.platform.platform.exception.CranialException;
import io.cranial.platform.platform.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ToolRegistryService {

    private final ToolMapper toolMapper;
    private final SchemaMapper schemaMapper;
    private final ToolExecutor toolExecutor;

    public ToolRegistryService(ToolMapper toolMapper, SchemaMapper schemaMapper, ToolExecutor toolExecutor) {
        this.toolMapper = toolMapper;
        this.schemaMapper = schemaMapper;
        this.toolExecutor = toolExecutor;
    }

    public List<ToolView> listTools() {
        Map<UUID, SchemaEntity> schemas = schemaMapper.selectList(null).stream()
                .collect(Collectors.toMap(SchemaEntity::getId, s -> s));

        return toolMapper.selectList(null).stream()
                .map(t -> toView(t, schemas.get(t.getInputSchemaId()), schemas.get(t.getOutputSchemaId())))
                .toList();
    }

    public ToolView getTool(UUID id) {
        ToolEntity entity = toolMapper.selectById(id);
        if (entity == null) {
            throw new CranialException(ErrorCode.TOOL_NOT_FOUND, "Tool not found: " + id);
        }
        SchemaEntity in = entity.getInputSchemaId() != null ? schemaMapper.selectById(entity.getInputSchemaId()) : null;
        SchemaEntity out = entity.getOutputSchemaId() != null ? schemaMapper.selectById(entity.getOutputSchemaId()) : null;
        return toView(entity, in, out);
    }

    public ToolExecutionResult testRunTool(UUID id, ToolExecutionRequest request) {
        ToolEntity tool = toolMapper.selectById(id);
        if (tool == null) {
            throw new CranialException(ErrorCode.TOOL_NOT_FOUND, "Tool not found: " + id);
        }
        return toolExecutor.execute(tool, request);
    }

    @Transactional
    public ToolView saveTool(
            UUID id,
            String toolCode,
            String name,
            String description,
            UUID applicationId,
            UUID inputSchemaId,
            UUID outputSchemaId,
            String executionType,
            String endpointUrl,
            Boolean isReadOnly,
            String requiredPermission,
            String riskLevel,
            Integer timeoutMs,
            String status
    ) {
        ToolEntity entity = id != null ? toolMapper.selectById(id) : null;
        boolean create = false;
        if (entity == null) {
            entity = new ToolEntity();
            entity.setId(id != null ? id : UUID.randomUUID());
            entity.setCreatedAt(Instant.now());
            create = true;
        }

        entity.setToolCode(toolCode.trim().toLowerCase());
        entity.setName(name.trim());
        entity.setDescription(description != null ? description.trim() : "");
        entity.setApplicationId(applicationId);
        entity.setInputSchemaId(inputSchemaId);
        entity.setOutputSchemaId(outputSchemaId);
        entity.setExecutionType(executionType != null ? executionType.toUpperCase() : "INTERNAL_MOCK");
        entity.setEndpointUrl(endpointUrl);
        entity.setIsReadOnly(isReadOnly == null || isReadOnly);
        entity.setRequiredPermission(requiredPermission != null ? requiredPermission.trim() : "app:read");
        entity.setRiskLevel(riskLevel != null ? riskLevel.toUpperCase() : "LOW");
        entity.setTimeoutMs(timeoutMs != null && timeoutMs > 0 ? timeoutMs : 5000);
        entity.setStatus(status != null ? status.toUpperCase() : "ACTIVE");
        entity.setUpdatedAt(Instant.now());

        if (create) {
            toolMapper.insert(entity);
        } else {
            toolMapper.updateById(entity);
        }

        SchemaEntity in = inputSchemaId != null ? schemaMapper.selectById(inputSchemaId) : null;
        SchemaEntity out = outputSchemaId != null ? schemaMapper.selectById(outputSchemaId) : null;
        return toView(entity, in, out);
    }

    private ToolView toView(ToolEntity t, SchemaEntity in, SchemaEntity out) {
        return new ToolView(
                t.getId(),
                t.getToolCode(),
                t.getName(),
                t.getDescription(),
                t.getApplicationId(),
                t.getInputSchemaId(),
                in != null ? in.getSchemaCode() : null,
                t.getOutputSchemaId(),
                out != null ? out.getSchemaCode() : null,
                t.getExecutionType(),
                t.getEndpointUrl(),
                Boolean.TRUE.equals(t.getIsReadOnly()),
                t.getRequiredPermission(),
                t.getRiskLevel(),
                t.getTimeoutMs() != null ? t.getTimeoutMs() : 5000,
                t.getStatus(),
                t.getCreatedAt(),
                t.getUpdatedAt()
        );
    }
}

