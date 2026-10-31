package io.cranial.platform.application.featureflag;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cranial.platform.audit.AuditEvent;
import io.cranial.platform.audit.AuditService;
import io.cranial.platform.platform.exception.CranialException;
import io.cranial.platform.platform.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class FeatureFlagService {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    private final FeatureFlagMapper flagMapper;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public FeatureFlagService(
            FeatureFlagMapper flagMapper,
            AuditService auditService,
            ObjectMapper objectMapper
    ) {
        this.flagMapper = flagMapper;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    public boolean isEnabled(String flagKey) {
        return isEnabled(flagKey, null);
    }

    public boolean isEnabled(String flagKey, String appCode) {
        if (flagKey == null || flagKey.isBlank()) {
            return false;
        }

        FeatureFlagEntity flag = flagMapper.selectOne(
                new LambdaQueryWrapper<FeatureFlagEntity>().eq(FeatureFlagEntity::getFlagKey, flagKey.trim())
        );

        if (flag == null || !Boolean.TRUE.equals(flag.getEnabled())) {
            return false;
        }

        List<String> allowedApps = parseList(flag.getAllowedAppCodes());
        if (appCode != null && !appCode.isBlank()) {
            String normalizedApp = appCode.trim().toLowerCase(Locale.ROOT);
            if (allowedApps.contains(normalizedApp)) {
                return true;
            }
        }

        int percentage = flag.getRolloutPercentage() != null ? flag.getRolloutPercentage() : 100;
        if (percentage >= 100) {
            return allowedApps.isEmpty() || (appCode != null && allowedApps.contains(appCode.toLowerCase(Locale.ROOT)));
        }
        if (percentage <= 0) {
            return false;
        }

        if (appCode != null && !appCode.isBlank()) {
            int hash = Math.abs((flagKey + ":" + appCode.toLowerCase(Locale.ROOT)).hashCode());
            return (hash % 100) < percentage;
        }

        return true;
    }

    public List<FeatureFlagView> listFlags() {
        return flagMapper.selectList(
                new LambdaQueryWrapper<FeatureFlagEntity>().orderByAsc(FeatureFlagEntity::getFlagKey)
        ).stream().map(this::toView).toList();
    }

    @Transactional
    public FeatureFlagView updateFlag(
            String flagKey,
            boolean enabled,
            int rolloutPercentage,
            List<String> allowedAppCodes,
            UUID actorUserId,
            String ipAddress
    ) {
        FeatureFlagEntity flag = flagMapper.selectOne(
                new LambdaQueryWrapper<FeatureFlagEntity>().eq(FeatureFlagEntity::getFlagKey, flagKey.trim())
        );
        if (flag == null) {
            throw new CranialException(ErrorCode.RESOURCE_NOT_FOUND, "Feature flag not found: " + flagKey);
        }

        if (rolloutPercentage < 0 || rolloutPercentage > 100) {
            throw new CranialException(ErrorCode.VALIDATION_ERROR, "rollout_percentage must be between 0 and 100");
        }

        flag.setEnabled(enabled);
        flag.setRolloutPercentage(rolloutPercentage);
        flag.setAllowedAppCodes(toJson(allowedAppCodes != null ? allowedAppCodes : List.of()));
        flag.setUpdatedBy(actorUserId);
        flag.setUpdatedAt(Instant.now());
        flagMapper.updateById(flag);

        auditService.record(new AuditEvent(
                "PLATFORM_USER",
                actorUserId != null ? actorUserId.toString() : "system",
                null,
                null,
                "FEATURE_FLAG_UPDATED",
                "FEATURE_FLAG",
                flagKey,
                "SUCCESS",
                Map.of("enabled", enabled, "rollout_percentage", rolloutPercentage),
                ipAddress
        ));

        return toView(flag);
    }

    private List<String> parseList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, STRING_LIST);
        } catch (Exception ex) {
            return List.of();
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception ex) {
            return "[]";
        }
    }

    private FeatureFlagView toView(FeatureFlagEntity entity) {
        return new FeatureFlagView(
                entity.getId(),
                entity.getFlagKey(),
                entity.getName(),
                entity.getDescription(),
                Boolean.TRUE.equals(entity.getEnabled()),
                entity.getRolloutPercentage() != null ? entity.getRolloutPercentage() : 0,
                parseList(entity.getAllowedAppCodes()),
                entity.getUpdatedAt()
        );
    }
}

