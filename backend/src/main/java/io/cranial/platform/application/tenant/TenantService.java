package io.cranial.platform.application.tenant;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cranial.platform.application.registry.ApplicationRegistryService;
import io.cranial.platform.audit.AuditEvent;
import io.cranial.platform.audit.AuditService;
import io.cranial.platform.platform.common.ApiResponse;
import io.cranial.platform.platform.common.PageMeta;
import io.cranial.platform.platform.exception.CranialException;
import io.cranial.platform.platform.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class TenantService {

    private static final Set<String> VALID_STATUSES = Set.of("ACTIVE", "SUSPENDED");
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final TenantMapper tenantMapper;
    private final TenantMembershipMapper membershipMapper;
    private final ApplicationRegistryService registryService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public TenantService(
            TenantMapper tenantMapper,
            TenantMembershipMapper membershipMapper,
            ApplicationRegistryService registryService,
            AuditService auditService,
            ObjectMapper objectMapper
    ) {
        this.tenantMapper = tenantMapper;
        this.membershipMapper = membershipMapper;
        this.registryService = registryService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public TenantView provisionTenant(
            UUID applicationId,
            String externalTenantRef,
            String name,
            Map<String, Object> metadata,
            UUID actorUserId,
            String ipAddress
    ) {
        registryService.getRequiredEntity(applicationId);
        if (externalTenantRef == null || externalTenantRef.isBlank()) {
            throw new CranialException(ErrorCode.VALIDATION_ERROR, "external_tenant_ref is required");
        }
        String normalizedRef = externalTenantRef.trim();
        Long dup = tenantMapper.selectCount(new LambdaQueryWrapper<TenantEntity>()
                .eq(TenantEntity::getApplicationId, applicationId)
                .eq(TenantEntity::getExternalTenantRef, normalizedRef));
        if (dup != null && dup > 0) {
            throw new CranialException(ErrorCode.CONFLICT, "Tenant '" + normalizedRef + "' already provisioned for this application");
        }

        Instant now = Instant.now();
        TenantEntity entity = new TenantEntity();
        entity.setId(UUID.randomUUID());
        entity.setApplicationId(applicationId);
        entity.setExternalTenantRef(normalizedRef);
        entity.setName(name != null && !name.isBlank() ? name.trim() : normalizedRef);
        entity.setStatus("ACTIVE");
        entity.setMetadata(writeJson(metadata));
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        tenantMapper.insert(entity);

        auditService.record(new AuditEvent(
                "PLATFORM_USER",
                actorUserId != null ? actorUserId.toString() : "system",
                applicationId,
                entity.getId(),
                "TENANT_PROVISIONED",
                "TENANT",
                entity.getId().toString(),
                "SUCCESS",
                Map.of("external_tenant_ref", normalizedRef),
                ipAddress
        ));

        return toView(entity);
    }

    @Transactional
    public TenantView updateStatus(UUID tenantId, String newStatus, UUID actorUserId, String ipAddress) {
        String normalized = newStatus == null ? "" : newStatus.trim().toUpperCase(Locale.ROOT);
        if (!VALID_STATUSES.contains(normalized)) {
            throw new CranialException(ErrorCode.VALIDATION_ERROR, "Tenant status must be ACTIVE or SUSPENDED");
        }
        TenantEntity entity = tenantMapper.selectById(tenantId);
        if (entity == null) {
            throw new CranialException(ErrorCode.RESOURCE_NOT_FOUND, "Tenant not found: " + tenantId);
        }
        String previous = entity.getStatus();
        entity.setStatus(normalized);
        entity.setUpdatedAt(Instant.now());
        tenantMapper.updateById(entity);

        auditService.record(new AuditEvent(
                "PLATFORM_USER",
                actorUserId != null ? actorUserId.toString() : "system",
                entity.getApplicationId(),
                entity.getId(),
                "TENANT_STATUS_UPDATED",
                "TENANT",
                entity.getId().toString(),
                "SUCCESS",
                Map.of("previous_status", previous, "new_status", normalized),
                ipAddress
        ));

        return toView(entity);
    }

    @Transactional
    public TenantMembershipEntity upsertMembership(
            UUID tenantId,
            String externalUserRef,
            String memberRole,
            String status,
            UUID actorUserId,
            String ipAddress
    ) {
        TenantEntity tenant = tenantMapper.selectById(tenantId);
        if (tenant == null) {
            throw new CranialException(ErrorCode.RESOURCE_NOT_FOUND, "Tenant not found: " + tenantId);
        }
        if (externalUserRef == null || externalUserRef.isBlank()) {
            throw new CranialException(ErrorCode.VALIDATION_ERROR, "external_user_ref is required");
        }
        String normalizedStatus = status != null ? status.trim().toUpperCase(Locale.ROOT) : "ACTIVE";
        if (!VALID_STATUSES.contains(normalizedStatus)) {
            throw new CranialException(ErrorCode.VALIDATION_ERROR, "Membership status must be ACTIVE or SUSPENDED");
        }
        String normalizedUser = externalUserRef.trim();
        String role = (memberRole != null && !memberRole.isBlank()) ? memberRole.trim() : "MEMBER";
        Instant now = Instant.now();

        TenantMembershipEntity existing = membershipMapper.selectOne(
                new LambdaQueryWrapper<TenantMembershipEntity>()
                        .eq(TenantMembershipEntity::getTenantId, tenantId)
                        .eq(TenantMembershipEntity::getExternalUserRef, normalizedUser)
        );
        if (existing == null) {
            existing = new TenantMembershipEntity();
            existing.setId(UUID.randomUUID());
            existing.setTenantId(tenantId);
            existing.setExternalUserRef(normalizedUser);
            existing.setMemberRole(role);
            existing.setStatus(normalizedStatus);
            existing.setCreatedAt(now);
            existing.setUpdatedAt(now);
            membershipMapper.insert(existing);
        } else {
            existing.setMemberRole(role);
            existing.setStatus(normalizedStatus);
            existing.setUpdatedAt(now);
            membershipMapper.updateById(existing);
        }

        auditService.record(new AuditEvent(
                "PLATFORM_USER",
                actorUserId != null ? actorUserId.toString() : "system",
                tenant.getApplicationId(),
                tenantId,
                "TENANT_MEMBERSHIP_UPDATED",
                "TENANT_MEMBERSHIP",
                existing.getId().toString(),
                "SUCCESS",
                Map.of("external_user_ref", normalizedUser, "member_role", role, "status", normalizedStatus),
                ipAddress
        ));
        return existing;
    }

    public List<TenantMembershipEntity> listMemberships(UUID tenantId) {
        TenantEntity tenant = tenantMapper.selectById(tenantId);
        if (tenant == null) {
            throw new CranialException(ErrorCode.RESOURCE_NOT_FOUND, "Tenant not found: " + tenantId);
        }
        return membershipMapper.selectList(new LambdaQueryWrapper<TenantMembershipEntity>()
                .eq(TenantMembershipEntity::getTenantId, tenantId)
                .orderByAsc(TenantMembershipEntity::getExternalUserRef));
    }

    public ApiResponse<List<TenantView>> listTenants(UUID applicationId, String status, int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, size));
        LambdaQueryWrapper<TenantEntity> query = new LambdaQueryWrapper<>();
        if (applicationId != null) {
            query.eq(TenantEntity::getApplicationId, applicationId);
        }
        if (status != null && !status.isBlank()) {
            query.eq(TenantEntity::getStatus, status.trim().toUpperCase(Locale.ROOT));
        }
        query.orderByDesc(TenantEntity::getCreatedAt);

        Page<TenantEntity> result = tenantMapper.selectPage(new Page<>(safePage, safeSize), query);
        List<TenantView> views = result.getRecords().stream().map(this::toView).toList();
        return ApiResponse.paged(views, PageMeta.of(safePage, safeSize, result.getTotal()));
    }

    /**
     * Enforces strict tenant isolation: verifies externalTenantRef belongs to authenticated applicationId,
     * is ACTIVE, and (if tenant memberships exist for that tenant and externalUserRef is provided)
     * confirms user membership is not suspended.
     */
    public TenantEntity verifyAuthorizedTenant(
            UUID applicationId,
            String applicationCode,
            String externalTenantRef,
            String externalUserRef,
            String ipAddress
    ) {
        TenantEntity tenant = tenantMapper.selectOne(new LambdaQueryWrapper<TenantEntity>()
                .eq(TenantEntity::getApplicationId, applicationId)
                .eq(TenantEntity::getExternalTenantRef, externalTenantRef));
        if (tenant == null || !"ACTIVE".equalsIgnoreCase(tenant.getStatus())) {
            auditService.record(new AuditEvent(
                    "APPLICATION",
                    applicationCode,
                    applicationId,
                    tenant != null ? tenant.getId() : null,
                    "TENANT_ISOLATION_DENIED",
                    "TENANT",
                    externalTenantRef,
                    "DENIED",
                    Map.of("external_tenant_ref", externalTenantRef, "reason", tenant == null ? "NOT_PROVISIONED_FOR_APP" : "SUSPENDED"),
                    ipAddress
            ));
            throw new CranialException(
                    ErrorCode.TENANT_NOT_AUTHORIZED,
                    "Tenant '" + externalTenantRef + "' is not authorized or active for application '" + applicationCode + "'"
            );
        }

        if (externalUserRef != null && !externalUserRef.isBlank()) {
            Long membershipCount = membershipMapper.selectCount(new LambdaQueryWrapper<TenantMembershipEntity>()
                    .eq(TenantMembershipEntity::getTenantId, tenant.getId()));
            if (membershipCount != null && membershipCount > 0) {
                TenantMembershipEntity membership = membershipMapper.selectOne(new LambdaQueryWrapper<TenantMembershipEntity>()
                        .eq(TenantMembershipEntity::getTenantId, tenant.getId())
                        .eq(TenantMembershipEntity::getExternalUserRef, externalUserRef.trim()));
                if (membership == null || !"ACTIVE".equalsIgnoreCase(membership.getStatus())) {
                    auditService.record(new AuditEvent(
                            "APPLICATION",
                            applicationCode,
                            applicationId,
                            tenant.getId(),
                            "TENANT_USER_MEMBERSHIP_DENIED",
                            "TENANT_MEMBERSHIP",
                            externalUserRef,
                            "DENIED",
                            Map.of("external_tenant_ref", externalTenantRef, "external_user_ref", externalUserRef),
                            ipAddress
                    ));
                    throw new CranialException(
                            ErrorCode.TENANT_NOT_AUTHORIZED,
                            "User is not an active member of tenant '" + externalTenantRef + "'"
                    );
                }
            }
        }
        return tenant;
    }

    public long countActive() {
        return tenantMapper.selectCount(new LambdaQueryWrapper<TenantEntity>()
                .eq(TenantEntity::getStatus, "ACTIVE"));
    }

    private String writeJson(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private TenantView toView(TenantEntity entity) {
        Map<String, Object> meta = Map.of();
        if (entity.getMetadata() != null && !entity.getMetadata().isBlank()) {
            try {
                meta = objectMapper.readValue(entity.getMetadata(), MAP_TYPE);
            } catch (Exception ignored) {
                meta = Map.of();
            }
        }
        return new TenantView(
                entity.getId(),
                entity.getApplicationId(),
                entity.getExternalTenantRef(),
                entity.getName(),
                entity.getStatus(),
                meta,
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}

