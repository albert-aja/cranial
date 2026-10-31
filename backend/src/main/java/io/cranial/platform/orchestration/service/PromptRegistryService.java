package io.cranial.platform.orchestration.service;

import io.cranial.platform.orchestration.entity.PromptEntity;
import io.cranial.platform.orchestration.mapper.PromptMapper;
import io.cranial.platform.orchestration.model.PromptView;
import io.cranial.platform.orchestration.prompt.PromptInterpolator;
import io.cranial.platform.platform.exception.CranialException;
import io.cranial.platform.platform.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class PromptRegistryService {

    private final PromptMapper promptMapper;
    private final PromptInterpolator interpolator;

    public PromptRegistryService(PromptMapper promptMapper, PromptInterpolator interpolator) {
        this.promptMapper = promptMapper;
        this.interpolator = interpolator;
    }

    public List<PromptView> listPrompts() {
        return promptMapper.selectList(null).stream()
                .map(this::toView)
                .toList();
    }

    public PromptView getPrompt(UUID id) {
        PromptEntity entity = promptMapper.selectById(id);
        if (entity == null) {
            throw new CranialException(ErrorCode.PROMPT_NOT_FOUND, "Prompt not found: " + id);
        }
        return toView(entity);
    }

    public PromptEntity getRequiredEntity(UUID id) {
        PromptEntity entity = promptMapper.selectById(id);
        if (entity == null) {
            throw new CranialException(ErrorCode.PROMPT_NOT_FOUND, "Prompt not found: " + id);
        }
        return entity;
    }

    public List<PromptView> listVersions(String promptCode) {
        return promptMapper.findVersionsByCode(promptCode.trim().toLowerCase()).stream()
                .map(this::toView)
                .toList();
    }

    @Transactional
    public PromptView createOrUpdatePrompt(
            UUID id,
            String promptCode,
            String name,
            String description,
            String capabilityCode,
            String taskCode,
            UUID applicationId,
            Integer version,
            String status,
            String systemTemplate,
            String userTemplate,
            String inputVariablesJson
    ) {
        PromptEntity entity = id != null ? promptMapper.selectById(id) : null;
        boolean create = false;
        if (entity == null) {
            entity = new PromptEntity();
            entity.setId(id != null ? id : UUID.randomUUID());
            entity.setCreatedAt(Instant.now());
            create = true;
        }

        entity.setPromptCode(promptCode.trim().toLowerCase());
        entity.setName(name.trim());
        entity.setDescription(description);
        entity.setCapabilityCode(capabilityCode.trim().toLowerCase());
        entity.setTaskCode(taskCode != null && !taskCode.isBlank() ? taskCode.trim().toLowerCase() : null);
        entity.setApplicationId(applicationId);
        entity.setVersion(version != null && version > 0 ? version : 1);
        entity.setStatus(status != null ? status.toUpperCase() : "PUBLISHED");
        entity.setSystemTemplate(systemTemplate != null ? systemTemplate : "");
        entity.setUserTemplate(userTemplate != null ? userTemplate : "");
        entity.setInputVariablesJson(inputVariablesJson != null ? inputVariablesJson : "[]");
        entity.setUpdatedAt(Instant.now());

        if (create) {
            promptMapper.insert(entity);
        } else {
            promptMapper.updateById(entity);
        }

        return toView(entity);
    }

    @Transactional
    public PromptView publishVersion(UUID id) {
        PromptEntity target = getRequiredEntity(id);

        // Retire all other versions of this prompt
        List<PromptEntity> versions = promptMapper.findVersionsByCode(target.getPromptCode());
        for (PromptEntity v : versions) {
            if (!v.getId().equals(target.getId()) && "PUBLISHED".equals(v.getStatus())) {
                v.setStatus("RETIRED");
                v.setUpdatedAt(Instant.now());
                promptMapper.updateById(v);
            }
        }

        target.setStatus("PUBLISHED");
        target.setUpdatedAt(Instant.now());
        promptMapper.updateById(target);

        return toView(target);
    }

    private PromptView toView(PromptEntity e) {
        List<String> vars = interpolator.parseRequiredVariables(e.getInputVariablesJson());
        return new PromptView(
                e.getId(),
                e.getPromptCode(),
                e.getName(),
                e.getDescription(),
                e.getCapabilityCode(),
                e.getTaskCode(),
                e.getApplicationId(),
                e.getVersion() != null ? e.getVersion() : 1,
                e.getStatus(),
                e.getSystemTemplate(),
                e.getUserTemplate(),
                vars,
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }
}

