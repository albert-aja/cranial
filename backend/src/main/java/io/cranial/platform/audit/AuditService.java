package io.cranial.platform.audit;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cranial.platform.platform.common.ApiResponse;
import io.cranial.platform.platform.common.PageMeta;
import io.cranial.platform.platform.observability.CorrelationIdHolder;
import io.cranial.platform.platform.observability.LogSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final AuditLogMapper auditLogMapper;
    private final ObjectMapper objectMapper;

    public AuditService(AuditLogMapper auditLogMapper, ObjectMapper objectMapper) {
        this.auditLogMapper = auditLogMapper;
        this.objectMapper = objectMapper;
    }

    public void record(AuditEvent event) {
        try {
            Map<String, Object> safeMetadata = LogSanitizer.sanitizeMap(event.metadata());
            String metadataJson = objectMapper.writeValueAsString(safeMetadata);

            AuditLogEntity entity = new AuditLogEntity();
            entity.setId(UUID.randomUUID());
            entity.setCorrelationId(CorrelationIdHolder.currentOrGenerate());
            entity.setActorType(event.actorType());
            entity.setActorId(event.actorId());
            entity.setApplicationId(event.applicationId());
            entity.setTenantId(event.tenantId());
            entity.setAction(event.action());
            entity.setTargetType(event.targetType());
            entity.setTargetId(event.targetId());
            entity.setOutcome(event.outcome());
            entity.setMetadata(metadataJson);
            entity.setIpAddress(event.ipAddress());
            entity.setCreatedAt(Instant.now());

            auditLogMapper.insert(entity);
        } catch (Exception ex) {
            log.error("Failed to persist audit record action={} outcome={}", event.action(), event.outcome(), ex);
        }
    }

    public ApiResponse<List<AuditLogView>> search(
            UUID applicationId,
            String outcome,
            String action,
            String correlationId,
            int page,
            int size
    ) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, size));

        LambdaQueryWrapper<AuditLogEntity> query = new LambdaQueryWrapper<>();
        if (applicationId != null) {
            query.eq(AuditLogEntity::getApplicationId, applicationId);
        }
        if (outcome != null && !outcome.isBlank()) {
            query.eq(AuditLogEntity::getOutcome, outcome.trim().toUpperCase());
        }
        if (action != null && !action.isBlank()) {
            query.eq(AuditLogEntity::getAction, action.trim());
        }
        if (correlationId != null && !correlationId.isBlank()) {
            query.eq(AuditLogEntity::getCorrelationId, correlationId.trim());
        }
        query.orderByDesc(AuditLogEntity::getCreatedAt);

        Page<AuditLogEntity> result = auditLogMapper.selectPage(new Page<>(safePage, safeSize), query);
        List<AuditLogView> views = result.getRecords().stream()
                .map(this::toView)
                .toList();
        return ApiResponse.paged(views, PageMeta.of(safePage, safeSize, result.getTotal()));
    }

    public long countByOutcome(String outcome) {
        return auditLogMapper.selectCount(new LambdaQueryWrapper<AuditLogEntity>()
                .eq(AuditLogEntity::getOutcome, outcome));
    }

    private AuditLogView toView(AuditLogEntity entity) {
        Map<String, Object> parsedMeta = Map.of();
        if (entity.getMetadata() != null && !entity.getMetadata().isBlank()) {
            try {
                parsedMeta = objectMapper.readValue(entity.getMetadata(), MAP_TYPE);
            } catch (Exception ignored) {
                parsedMeta = Map.of();
            }
        }
        return new AuditLogView(
                entity.getId(),
                entity.getCorrelationId(),
                entity.getActorType(),
                entity.getActorId(),
                entity.getApplicationId(),
                entity.getTenantId(),
                entity.getAction(),
                entity.getTargetType(),
                entity.getTargetId(),
                entity.getOutcome(),
                parsedMeta,
                entity.getIpAddress(),
                entity.getCreatedAt()
        );
    }
}

