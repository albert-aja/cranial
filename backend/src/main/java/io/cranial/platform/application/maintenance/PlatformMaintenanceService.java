package io.cranial.platform.application.maintenance;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.cranial.platform.application.credential.ApplicationCredentialEntity;
import io.cranial.platform.application.credential.ApplicationCredentialMapper;
import io.cranial.platform.application.featureflag.FeatureFlagService;
import io.cranial.platform.application.idempotency.IdempotencyRecordEntity;
import io.cranial.platform.application.idempotency.IdempotencyRecordMapper;
import io.cranial.platform.audit.AuditEvent;
import io.cranial.platform.audit.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PlatformMaintenanceService {

    private static final Logger log = LoggerFactory.getLogger(PlatformMaintenanceService.class);
    private static final String FLAG_AUTO_CLEANUP = "system.maintenance.auto_cleanup";

    private final IdempotencyRecordMapper idempotencyMapper;
    private final ApplicationCredentialMapper credentialMapper;
    private final FeatureFlagService featureFlagService;
    private final AuditService auditService;
    private final List<MaintenanceCleanupContributor> contributors;

    public PlatformMaintenanceService(
            IdempotencyRecordMapper idempotencyMapper,
            ApplicationCredentialMapper credentialMapper,
            FeatureFlagService featureFlagService,
            AuditService auditService,
            List<MaintenanceCleanupContributor> contributors
    ) {
        this.idempotencyMapper = idempotencyMapper;
        this.credentialMapper = credentialMapper;
        this.featureFlagService = featureFlagService;
        this.auditService = auditService;
        this.contributors = contributors != null ? contributors : List.of();
    }

    public record MaintenanceSummary(
            int expiredIdempotencyPurged,
            int rotatingCredentialsExpired,
            long durationMs,
            Instant timestamp
    ) {
    }

    @Scheduled(cron = "${cranial.maintenance.cron:0 0 * * * *}")
    public void runScheduledMaintenance() {
        if (!featureFlagService.isEnabled(FLAG_AUTO_CLEANUP)) {
            log.debug("Platform auto cleanup is disabled via feature flag {}", FLAG_AUTO_CLEANUP);
            return;
        }
        log.info("Starting scheduled platform maintenance execution...");
        runMaintenance("SCHEDULED", null, "127.0.0.1");
    }

    @Transactional
    public MaintenanceSummary runMaintenance(String triggerType, String actorId, String ipAddress) {
        long start = System.currentTimeMillis();
        Instant now = Instant.now();

        // 1. Purge expired idempotency records
        int purgedIdempotency = idempotencyMapper.delete(
                new LambdaQueryWrapper<IdempotencyRecordEntity>()
                        .lt(IdempotencyRecordEntity::getExpiresAt, now)
        );

        // 2. Transition expired rotating credentials to EXPIRED status
        int expiredCreds = credentialMapper.update(
                null,
                new LambdaUpdateWrapper<ApplicationCredentialEntity>()
                        .set(ApplicationCredentialEntity::getStatus, "EXPIRED")
                        .eq(ApplicationCredentialEntity::getStatus, "ROTATING")
                        .lt(ApplicationCredentialEntity::getExpiresAt, now)
        );

        Map<String, Object> auditMetadata = new LinkedHashMap<>();
        auditMetadata.put("expired_idempotency_purged", purgedIdempotency);
        auditMetadata.put("rotating_credentials_expired", expiredCreds);

        for (MaintenanceCleanupContributor contributor : contributors) {
            try {
                Map<String, Object> sub = contributor.cleanupExpired(now);
                if (sub != null) {
                    auditMetadata.put(contributor.contributorName(), sub);
                }
            } catch (Exception ex) {
                log.warn("Maintenance contributor {} encountered error: {}", contributor.contributorName(), ex.getMessage());
                auditMetadata.put(contributor.contributorName() + "_error", ex.getMessage());
            }
        }

        long duration = System.currentTimeMillis() - start;
        auditMetadata.put("duration_ms", duration);

        auditService.record(new AuditEvent(
                "SYSTEM",
                actorId != null ? actorId : "scheduler",
                null,
                null,
                "PLATFORM_MAINTENANCE_RUN",
                "SYSTEM",
                triggerType,
                "SUCCESS",
                auditMetadata,
                ipAddress
        ));

        log.info("Platform maintenance completed in {} ms: purged {} idempotency records, transitioned {} rotating credentials to EXPIRED",
                duration, purgedIdempotency, expiredCreds);

        return new MaintenanceSummary(purgedIdempotency, expiredCreds, duration, now);
    }
}

