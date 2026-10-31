package io.cranial.platform.orchestration.service;

import io.cranial.platform.orchestration.entity.PromptEntity;
import io.cranial.platform.orchestration.entity.SchemaEntity;
import io.cranial.platform.orchestration.entity.TaskDefinitionEntity;
import io.cranial.platform.orchestration.mapper.PromptMapper;
import io.cranial.platform.orchestration.mapper.SchemaMapper;
import io.cranial.platform.orchestration.mapper.TaskDefinitionMapper;
import io.cranial.platform.orchestration.model.TaskDefinitionView;
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
public class TaskRegistryService {

    private final TaskDefinitionMapper taskMapper;
    private final PromptMapper promptMapper;
    private final SchemaMapper schemaMapper;

    public TaskRegistryService(
            TaskDefinitionMapper taskMapper,
            PromptMapper promptMapper,
            SchemaMapper schemaMapper
    ) {
        this.taskMapper = taskMapper;
        this.promptMapper = promptMapper;
        this.schemaMapper = schemaMapper;
    }

    public List<TaskDefinitionView> listTasks() {
        Map<UUID, PromptEntity> prompts = promptMapper.selectList(null).stream()
                .collect(Collectors.toMap(PromptEntity::getId, p -> p));
        Map<UUID, SchemaEntity> schemas = schemaMapper.selectList(null).stream()
                .collect(Collectors.toMap(SchemaEntity::getId, s -> s));

        return taskMapper.selectList(null).stream()
                .map(t -> toView(t, prompts.get(t.getDefaultPromptId()), schemas.get(t.getOutputSchemaId())))
                .toList();
    }

    public TaskDefinitionView getTask(UUID id) {
        TaskDefinitionEntity entity = taskMapper.selectById(id);
        if (entity == null) {
            throw new CranialException(ErrorCode.TASK_NOT_FOUND, "Task definition not found: " + id);
        }
        PromptEntity p = entity.getDefaultPromptId() != null ? promptMapper.selectById(entity.getDefaultPromptId()) : null;
        SchemaEntity s = entity.getOutputSchemaId() != null ? schemaMapper.selectById(entity.getOutputSchemaId()) : null;
        return toView(entity, p, s);
    }

    public TaskDefinitionEntity getRequiredEntity(String taskCode) {
        return taskMapper.findActiveByCode(taskCode.trim().toLowerCase())
                .orElseThrow(() -> new CranialException(ErrorCode.TASK_NOT_FOUND, "Active task definition not found: " + taskCode));
    }

    @Transactional
    public TaskDefinitionView saveTask(
            UUID id,
            String taskCode,
            String name,
            String description,
            String capabilityCode,
            UUID defaultPromptId,
            UUID outputSchemaId,
            Integer timeoutSeconds,
            String allowedDataSensitivity,
            Boolean isActive
    ) {
        TaskDefinitionEntity entity = id != null ? taskMapper.selectById(id) : null;
        boolean create = false;
        if (entity == null) {
            entity = new TaskDefinitionEntity();
            entity.setId(id != null ? id : UUID.randomUUID());
            entity.setCreatedAt(Instant.now());
            create = true;
        }

        entity.setTaskCode(taskCode.trim().toLowerCase());
        entity.setName(name.trim());
        entity.setDescription(description);
        entity.setCapabilityCode(capabilityCode.trim().toLowerCase());
        entity.setDefaultPromptId(defaultPromptId);
        entity.setOutputSchemaId(outputSchemaId);
        entity.setTimeoutSeconds(timeoutSeconds != null && timeoutSeconds > 0 ? timeoutSeconds : 60);
        entity.setAllowedDataSensitivity(allowedDataSensitivity != null ? allowedDataSensitivity.toUpperCase() : "RESTRICTED");
        entity.setIsActive(isActive == null || isActive);
        entity.setUpdatedAt(Instant.now());

        if (create) {
            taskMapper.insert(entity);
        } else {
            taskMapper.updateById(entity);
        }

        PromptEntity p = defaultPromptId != null ? promptMapper.selectById(defaultPromptId) : null;
        SchemaEntity s = outputSchemaId != null ? schemaMapper.selectById(outputSchemaId) : null;
        return toView(entity, p, s);
    }

    private TaskDefinitionView toView(TaskDefinitionEntity t, PromptEntity p, SchemaEntity s) {
        return new TaskDefinitionView(
                t.getId(),
                t.getTaskCode(),
                t.getName(),
                t.getDescription(),
                t.getCapabilityCode(),
                t.getDefaultPromptId(),
                p != null ? p.getPromptCode() : null,
                t.getOutputSchemaId(),
                s != null ? s.getSchemaCode() : null,
                t.getTimeoutSeconds() != null ? t.getTimeoutSeconds() : 60,
                t.getAllowedDataSensitivity(),
                Boolean.TRUE.equals(t.getIsActive()),
                t.getCreatedAt(),
                t.getUpdatedAt()
        );
    }
}

