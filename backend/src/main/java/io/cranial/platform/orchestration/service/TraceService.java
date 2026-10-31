package io.cranial.platform.orchestration.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cranial.platform.orchestration.entity.OrchestratorTraceEntity;
import io.cranial.platform.orchestration.entity.PromptEntity;
import io.cranial.platform.orchestration.entity.SchemaEntity;
import io.cranial.platform.orchestration.mapper.OrchestratorTraceMapper;
import io.cranial.platform.orchestration.mapper.PromptMapper;
import io.cranial.platform.orchestration.mapper.SchemaMapper;
import io.cranial.platform.orchestration.model.OrchestrationStep;
import io.cranial.platform.orchestration.model.OrchestratorTraceView;
import io.cranial.platform.platform.exception.CranialException;
import io.cranial.platform.platform.exception.ErrorCode;
import io.cranial.platform.provider.entity.ModelEntity;
import io.cranial.platform.provider.mapper.ModelMapper;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TraceService {

    private static final TypeReference<List<OrchestrationStep>> STEP_LIST_TYPE = new TypeReference<>() {};

    private final OrchestratorTraceMapper traceMapper;
    private final ModelMapper modelMapper;
    private final PromptMapper promptMapper;
    private final SchemaMapper schemaMapper;
    private final ObjectMapper objectMapper;

    public TraceService(
            OrchestratorTraceMapper traceMapper,
            ModelMapper modelMapper,
            PromptMapper promptMapper,
            SchemaMapper schemaMapper,
            ObjectMapper objectMapper
    ) {
        this.traceMapper = traceMapper;
        this.modelMapper = modelMapper;
        this.promptMapper = promptMapper;
        this.schemaMapper = schemaMapper;
        this.objectMapper = objectMapper;
    }

    public List<OrchestratorTraceView> listRecent(UUID applicationId, int limit) {
        Map<UUID, ModelEntity> models = modelMapper.selectList(null).stream()
                .collect(Collectors.toMap(ModelEntity::getId, m -> m));
        Map<UUID, PromptEntity> prompts = promptMapper.selectList(null).stream()
                .collect(Collectors.toMap(PromptEntity::getId, p -> p));
        Map<UUID, SchemaEntity> schemas = schemaMapper.selectList(null).stream()
                .collect(Collectors.toMap(SchemaEntity::getId, s -> s));

        return traceMapper.selectList(null).stream()
                .filter(t -> applicationId == null || applicationId.equals(t.getApplicationId()))
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .limit(Math.min(limit, 100))
                .map(t -> toView(t, models.get(t.getModelId()), prompts.get(t.getPromptId()), schemas.get(t.getSchemaId())))
                .toList();
    }

    public OrchestratorTraceView getTrace(UUID id) {
        OrchestratorTraceEntity t = traceMapper.selectById(id);
        if (t == null) {
            throw new CranialException(ErrorCode.RESOURCE_NOT_FOUND, "Trace not found: " + id);
        }
        ModelEntity m = t.getModelId() != null ? modelMapper.selectById(t.getModelId()) : null;
        PromptEntity p = t.getPromptId() != null ? promptMapper.selectById(t.getPromptId()) : null;
        SchemaEntity s = t.getSchemaId() != null ? schemaMapper.selectById(t.getSchemaId()) : null;
        return toView(t, m, p, s);
    }

    private OrchestratorTraceView toView(OrchestratorTraceEntity t, ModelEntity m, PromptEntity p, SchemaEntity s) {
        List<OrchestrationStep> steps = Collections.emptyList();
        if (t.getStepsJson() != null && !t.getStepsJson().isBlank()) {
            try {
                steps = objectMapper.readValue(t.getStepsJson(), STEP_LIST_TYPE);
            } catch (Exception ignored) {
            }
        }

        return new OrchestratorTraceView(
                t.getId(),
                t.getApplicationId(),
                t.getTenantId(),
                t.getCorrelationId(),
                t.getTaskCode(),
                t.getCapabilityCode(),
                t.getModelId(),
                m != null ? m.getModelIdentifier() : null,
                t.getPromptId(),
                p != null ? p.getPromptCode() : null,
                t.getSchemaId(),
                s != null ? s.getSchemaCode() : null,
                t.getStatus(),
                t.getTotalDurationMs() != null ? t.getTotalDurationMs() : 0L,
                t.getPromptTokens() != null ? t.getPromptTokens() : 0,
                t.getCompletionTokens() != null ? t.getCompletionTokens() : 0,
                t.getToolInvocationsCount() != null ? t.getToolInvocationsCount() : 0,
                steps,
                t.getErrorMessage(),
                t.getCreatedAt()
        );
    }
}

