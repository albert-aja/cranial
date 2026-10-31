package io.cranial.platform.application.capability;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.cranial.platform.application.registry.ApplicationEntity;
import io.cranial.platform.application.registry.ApplicationRegistryService;
import io.cranial.platform.audit.AuditEvent;
import io.cranial.platform.audit.AuditService;
import io.cranial.platform.platform.exception.CranialException;
import io.cranial.platform.platform.exception.ErrorCode;
import io.cranial.platform.platform.observability.PlatformMetrics;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class CapabilityEntitlementService {

    private final AiCapabilityMapper capabilityMapper;
    private final ApplicationCapabilityMapper entitlementMapper;
    private final ApplicationRegistryService registryService;
    private final AuditService auditService;
    private final PlatformMetrics metrics;

    public CapabilityEntitlementService(
            AiCapabilityMapper capabilityMapper,
            ApplicationCapabilityMapper entitlementMapper,
            ApplicationRegistryService registryService,
            AuditService auditService,
            PlatformMetrics metrics
    ) {
        this.capabilityMapper = capabilityMapper;
        this.entitlementMapper = entitlementMapper;
        this.registryService = registryService;
        this.auditService = auditService;
        this.metrics = metrics;
    }

    public List<AiCapabilityEntity> listCatalog() {
        return capabilityMapper.selectList(
                new LambdaQueryWrapper<AiCapabilityEntity>().orderByAsc(AiCapabilityEntity::getCode)
        );
    }

    public List<CapabilityEntitlementView> listApplicationEntitlements(UUID applicationId) {
        registryService.getRequiredEntity(applicationId);
        List<AiCapabilityEntity> catalog = listCatalog();
        List<ApplicationCapabilityEntity> rows = entitlementMapper.selectList(
                new LambdaQueryWrapper<ApplicationCapabilityEntity>()
                        .eq(ApplicationCapabilityEntity::getApplicationId, applicationId)
        );
        Map<UUID, ApplicationCapabilityEntity> byCapId = new HashMap<>();
        rows.forEach(r -> byCapId.put(r.getCapabilityId(), r));

        return catalog.stream().map(cap -> {
            ApplicationCapabilityEntity row = byCapId.get(cap.getId());
            boolean enabled = row != null && Boolean.TRUE.equals(row.getEnabled());
            Integer quota = row != null ? row.getQuotaDailyRequests() : null;
            Instant updatedAt = row != null ? row.getUpdatedAt() : null;
            return new CapabilityEntitlementView(
                    cap.getId(),
                    cap.getCode(),
                    cap.getName(),
                    cap.getDescription(),
                    cap.getStatus(),
                    enabled,
                    quota,
                    updatedAt
            );
        }).toList();
    }

    public List<CapabilityEntitlementView> listEnabledForApplication(UUID applicationId) {
        return listApplicationEntitlements(applicationId).stream()
                .filter(e -> e.enabled() && "AVAILABLE".equalsIgnoreCase(e.platformStatus()))
                .toList();
    }

    @Transactional
    public CapabilityEntitlementView updateEntitlement(
            UUID applicationId,
            String capabilityCode,
            boolean enabled,
            Integer quotaDailyRequests,
            UUID actorUserId,
            String ipAddress
    ) {
        registryService.getRequiredEntity(applicationId);
        String normalizedCode = capabilityCode == null ? "" : capabilityCode.trim().toLowerCase(Locale.ROOT);
        AiCapabilityEntity cap = capabilityMapper.selectOne(
                new LambdaQueryWrapper<AiCapabilityEntity>().eq(AiCapabilityEntity::getCode, normalizedCode)
        );
        if (cap == null) {
            throw new CranialException(ErrorCode.RESOURCE_NOT_FOUND, "Capability not found: " + normalizedCode);
        }
        if (quotaDailyRequests != null && quotaDailyRequests < 0) {
            throw new CranialException(ErrorCode.VALIDATION_ERROR, "quota_daily_requests cannot be negative");
        }

        ApplicationCapabilityEntity existing = entitlementMapper.selectOne(
                new LambdaQueryWrapper<ApplicationCapabilityEntity>()
                        .eq(ApplicationCapabilityEntity::getApplicationId, applicationId)
                        .eq(ApplicationCapabilityEntity::getCapabilityId, cap.getId())
        );

        Instant now = Instant.now();
        if (existing == null) {
            existing = new ApplicationCapabilityEntity();
            existing.setId(UUID.randomUUID());
            existing.setApplicationId(applicationId);
            existing.setCapabilityId(cap.getId());
            existing.setEnabled(enabled);
            existing.setQuotaDailyRequests(quotaDailyRequests);
            existing.setConfigOverride("{}");
            existing.setUpdatedBy(actorUserId);
            existing.setCreatedAt(now);
            existing.setUpdatedAt(now);
            entitlementMapper.insert(existing);
        } else {
            existing.setEnabled(enabled);
            existing.setQuotaDailyRequests(quotaDailyRequests);
            existing.setUpdatedBy(actorUserId);
            existing.setUpdatedAt(now);
            entitlementMapper.updateById(existing);
        }

        Map<String, Object> meta = new HashMap<>();
        meta.put("capability_code", cap.getCode());
        meta.put("enabled", enabled);
        if (quotaDailyRequests != null) {
            meta.put("quota_daily_requests", quotaDailyRequests);
        }

        auditService.record(new AuditEvent(
                "PLATFORM_USER",
                actorUserId != null ? actorUserId.toString() : "system",
                applicationId,
                null,
                "CAPABILITY_ENTITLEMENT_UPDATED",
                "CAPABILITY",
                cap.getCode(),
                "SUCCESS",
                meta,
                ipAddress
        ));

        return new CapabilityEntitlementView(
                cap.getId(),
                cap.getCode(),
                cap.getName(),
                cap.getDescription(),
                cap.getStatus(),
                enabled,
                quotaDailyRequests,
                now
        );
    }

    public CapabilityEntitlementView assertEntitled(UUID applicationId, String capabilityCode, String ipAddress) {
        ApplicationEntity app = registryService.getRequiredEntity(applicationId);
        String normalizedCode = capabilityCode == null ? "" : capabilityCode.trim().toLowerCase(Locale.ROOT);
        AiCapabilityEntity cap = capabilityMapper.selectOne(
                new LambdaQueryWrapper<AiCapabilityEntity>().eq(AiCapabilityEntity::getCode, normalizedCode)
        );
        if (cap == null) {
            metrics.recordCapabilityCheck(app.getCode(), normalizedCode, false);
            throw new CranialException(ErrorCode.RESOURCE_NOT_FOUND, "Unknown capability: " + normalizedCode);
        }

        ApplicationCapabilityEntity entitlement = entitlementMapper.selectOne(
                new LambdaQueryWrapper<ApplicationCapabilityEntity>()
                        .eq(ApplicationCapabilityEntity::getApplicationId, applicationId)
                        .eq(ApplicationCapabilityEntity::getCapabilityId, cap.getId())
        );

        boolean allowed = "AVAILABLE".equalsIgnoreCase(cap.getStatus())
                && entitlement != null
                && Boolean.TRUE.equals(entitlement.getEnabled());

        metrics.recordCapabilityCheck(app.getCode(), cap.getCode(), allowed);

        if (!allowed) {
            auditService.record(new AuditEvent(
                    "APPLICATION",
                    app.getCode(),
                    applicationId,
                    null,
                    "CAPABILITY_ACCESS_DENIED",
                    "CAPABILITY",
                    cap.getCode(),
                    "DENIED",
                    Map.of("capability", cap.getCode()),
                    ipAddress
            ));
            throw new CranialException(
                    ErrorCode.CAPABILITY_NOT_ENTITLED,
                    "Application '" + app.getCode() + "' is not entitled to capability '" + cap.getCode() + "'"
            );
        }

        return new CapabilityEntitlementView(
                cap.getId(),
                cap.getCode(),
                cap.getName(),
                cap.getDescription(),
                cap.getStatus(),
                true,
                entitlement.getQuotaDailyRequests(),
                entitlement.getUpdatedAt()
        );
    }

    public long countEnabledEntitlements() {
        return entitlementMapper.selectCount(new LambdaQueryWrapper<ApplicationCapabilityEntity>()
                .eq(ApplicationCapabilityEntity::getEnabled, true));
    }
}

