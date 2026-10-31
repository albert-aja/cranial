package io.cranial.platform.application.credential;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.cranial.platform.application.registry.ApplicationEntity;
import io.cranial.platform.application.registry.ApplicationRegistryService;
import io.cranial.platform.application.registry.ApplicationSettingsEntity;
import io.cranial.platform.audit.AuditEvent;
import io.cranial.platform.audit.AuditService;
import io.cranial.platform.platform.crypto.CryptoService;
import io.cranial.platform.platform.exception.CranialException;
import io.cranial.platform.platform.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class ApplicationCredentialService {

    private static final String REVEAL_WARNING = "Store raw_api_key and raw_signing_secret securely now. Plaintext secrets are never stored and cannot be retrieved again.";

    private final ApplicationCredentialMapper credentialMapper;
    private final ApplicationRegistryService registryService;
    private final CryptoService cryptoService;
    private final AuditService auditService;

    public ApplicationCredentialService(
            ApplicationCredentialMapper credentialMapper,
            ApplicationRegistryService registryService,
            CryptoService cryptoService,
            AuditService auditService
    ) {
        this.credentialMapper = credentialMapper;
        this.registryService = registryService;
        this.cryptoService = cryptoService;
        this.auditService = auditService;
    }

    @Transactional
    public CredentialIssuedResult issueCredential(
            UUID applicationId,
            String name,
            Instant expiresAt,
            UUID actorUserId,
            String ipAddress
    ) {
        ApplicationEntity app = registryService.getRequiredEntity(applicationId);
        if ("RETIRED".equalsIgnoreCase(app.getStatus())) {
            throw new CranialException(ErrorCode.CONFLICT, "Cannot issue credentials for a retired application");
        }
        if (expiresAt != null && expiresAt.isBefore(Instant.now().plusSeconds(60))) {
            throw new CranialException(ErrorCode.VALIDATION_ERROR, "expires_at must be in the future");
        }

        CredentialIssuedResult issued = createAndInsertCredential(app, name, expiresAt, actorUserId);

        auditService.record(new AuditEvent(
                "PLATFORM_USER",
                actorUserId != null ? actorUserId.toString() : "system",
                applicationId,
                null,
                "CREDENTIAL_ISSUED",
                "CREDENTIAL",
                issued.credential().id().toString(),
                "SUCCESS",
                Map.of("key_prefix", issued.credential().keyPrefix(), "environment", app.getEnvironment()),
                ipAddress
        ));

        return issued;
    }

    @Transactional
    public CredentialIssuedResult rotateCredential(
            UUID applicationId,
            UUID credentialId,
            int gracePeriodMinutes,
            UUID actorUserId,
            String ipAddress
    ) {
        ApplicationEntity app = registryService.getRequiredEntity(applicationId);
        ApplicationCredentialEntity existing = getRequiredCredential(applicationId, credentialId);
        if ("REVOKED".equalsIgnoreCase(existing.getStatus()) || "EXPIRED".equalsIgnoreCase(existing.getStatus())) {
            throw new CranialException(ErrorCode.CONFLICT, "Cannot rotate a credential in status " + existing.getStatus());
        }

        int safeGraceMinutes = Math.max(0, Math.min(10080, gracePeriodMinutes));
        Instant now = Instant.now();
        if (safeGraceMinutes == 0) {
            existing.setStatus("REVOKED");
            existing.setRevokedAt(now);
            existing.setRevokedBy(actorUserId);
        } else {
            existing.setStatus("ROTATING");
            Instant graceExpiry = now.plus(Duration.ofMinutes(safeGraceMinutes));
            if (existing.getExpiresAt() == null || graceExpiry.isBefore(existing.getExpiresAt())) {
                existing.setExpiresAt(graceExpiry);
            }
        }
        credentialMapper.updateById(existing);

        String rotatedName = existing.getName() + " (rotated)";
        CredentialIssuedResult replacement = createAndInsertCredential(app, rotatedName, null, actorUserId);

        auditService.record(new AuditEvent(
                "PLATFORM_USER",
                actorUserId != null ? actorUserId.toString() : "system",
                applicationId,
                null,
                "CREDENTIAL_ROTATED",
                "CREDENTIAL",
                replacement.credential().id().toString(),
                "SUCCESS",
                Map.of(
                        "previous_credential_id", existing.getId().toString(),
                        "previous_key_prefix", existing.getKeyPrefix(),
                        "new_key_prefix", replacement.credential().keyPrefix(),
                        "grace_period_minutes", safeGraceMinutes
                ),
                ipAddress
        ));

        return replacement;
    }

    @Transactional
    public CredentialView revokeCredential(
            UUID applicationId,
            UUID credentialId,
            UUID actorUserId,
            String ipAddress
    ) {
        ApplicationCredentialEntity existing = getRequiredCredential(applicationId, credentialId);
        if (!"REVOKED".equalsIgnoreCase(existing.getStatus())) {
            existing.setStatus("REVOKED");
            existing.setRevokedAt(Instant.now());
            existing.setRevokedBy(actorUserId);
            credentialMapper.updateById(existing);
        }

        auditService.record(new AuditEvent(
                "PLATFORM_USER",
                actorUserId != null ? actorUserId.toString() : "system",
                applicationId,
                null,
                "CREDENTIAL_REVOKED",
                "CREDENTIAL",
                existing.getId().toString(),
                "SUCCESS",
                Map.of("key_prefix", existing.getKeyPrefix()),
                ipAddress
        ));

        return toView(existing);
    }

    public List<CredentialView> listForApplication(UUID applicationId) {
        registryService.getRequiredEntity(applicationId);
        List<ApplicationCredentialEntity> list = credentialMapper.selectList(
                new LambdaQueryWrapper<ApplicationCredentialEntity>()
                        .eq(ApplicationCredentialEntity::getApplicationId, applicationId)
                        .orderByDesc(ApplicationCredentialEntity::getCreatedAt)
        );
        return list.stream().map(this::toView).toList();
    }

    public VerifiedApplicationCredential verifyApiKey(String rawApiKey) {
        if (rawApiKey == null || rawApiKey.isBlank()) {
            throw new CranialException(ErrorCode.UNAUTHORIZED, "Missing X-Cranial-Api-Key header");
        }
        String trimmed = rawApiKey.trim();
        int dotIdx = trimmed.indexOf('.');
        if (dotIdx <= 4 || dotIdx == trimmed.length() - 1) {
            throw new CranialException(ErrorCode.INVALID_CREDENTIALS, "Malformed API key format");
        }
        String keyPrefix = trimmed.substring(0, dotIdx);
        String rawSecret = trimmed.substring(dotIdx + 1);

        ApplicationCredentialEntity cred = credentialMapper.selectOne(
                new LambdaQueryWrapper<ApplicationCredentialEntity>()
                        .eq(ApplicationCredentialEntity::getKeyPrefix, keyPrefix)
        );
        if (cred == null) {
            throw new CranialException(ErrorCode.INVALID_CREDENTIALS, "Invalid API key");
        }

        if (!cryptoService.verifyApiKeySecret(rawSecret, cred.getSecretHash())) {
            throw new CranialException(ErrorCode.INVALID_CREDENTIALS, "Invalid API key");
        }

        if ("REVOKED".equalsIgnoreCase(cred.getStatus())) {
            throw new CranialException(ErrorCode.CREDENTIAL_REVOKED, "API credential has been revoked");
        }

        Instant now = Instant.now();
        if ("EXPIRED".equalsIgnoreCase(cred.getStatus())
                || (cred.getExpiresAt() != null && now.isAfter(cred.getExpiresAt()))) {
            if (!"EXPIRED".equalsIgnoreCase(cred.getStatus())) {
                cred.setStatus("EXPIRED");
                credentialMapper.updateById(cred);
            }
            throw new CranialException(ErrorCode.CREDENTIAL_EXPIRED, "API credential has expired");
        }

        ApplicationEntity app = registryService.getRequiredEntity(cred.getApplicationId());
        if (!"ACTIVE".equalsIgnoreCase(app.getStatus())) {
            throw new CranialException(ErrorCode.APP_SUSPENDED, "Application is not active (status=" + app.getStatus() + ")");
        }

        ApplicationSettingsEntity settings = registryService.getRequiredSettings(app.getId());

        cred.setLastUsedAt(now);
        credentialMapper.updateById(cred);

        String signingSecret = cryptoService.decryptSecret(cred.getSigningSecretEncrypted());
        return new VerifiedApplicationCredential(
                cred.getId(),
                app.getId(),
                app.getCode(),
                app.getEnvironment(),
                cred.getKeyPrefix(),
                signingSecret,
                settings.getRateLimitRpm(),
                settings.getMaxInputBytes(),
                Boolean.TRUE.equals(settings.getRequireSignedContext()),
                Boolean.TRUE.equals(settings.getAllowHostedProviders()),
                Boolean.TRUE.equals(settings.getAllowSensitiveExternal())
        );
    }

    public long countActive() {
        return credentialMapper.selectCount(new LambdaQueryWrapper<ApplicationCredentialEntity>()
                .in(ApplicationCredentialEntity::getStatus, List.of("ACTIVE", "ROTATING")));
    }

    private CredentialIssuedResult createAndInsertCredential(
            ApplicationEntity app,
            String name,
            Instant expiresAt,
            UUID actorUserId
    ) {
        String envSlug = app.getEnvironment().toLowerCase(Locale.ROOT);
        String keyPrefix = "crn_" + envSlug + "_" + cryptoService.generateAlphaNumericPrefix(12);
        String rawSecret = cryptoService.generateRandomToken(32);
        String rawSigningSecret = cryptoService.generateRandomToken(32);
        String rawApiKey = keyPrefix + "." + rawSecret;

        ApplicationCredentialEntity entity = new ApplicationCredentialEntity();
        entity.setId(UUID.randomUUID());
        entity.setApplicationId(app.getId());
        entity.setName(name != null && !name.isBlank() ? name.trim() : "Default Key");
        entity.setEnvironment(app.getEnvironment());
        entity.setKeyPrefix(keyPrefix);
        entity.setSecretHash(cryptoService.hashApiKeySecret(rawSecret));
        entity.setSigningSecretEncrypted(cryptoService.encryptSecret(rawSigningSecret));
        entity.setStatus("ACTIVE");
        entity.setExpiresAt(expiresAt);
        entity.setCreatedBy(actorUserId);
        entity.setCreatedAt(Instant.now());

        credentialMapper.insert(entity);
        return new CredentialIssuedResult(toView(entity), rawApiKey, rawSigningSecret, REVEAL_WARNING);
    }

    private ApplicationCredentialEntity getRequiredCredential(UUID applicationId, UUID credentialId) {
        ApplicationCredentialEntity cred = credentialMapper.selectById(credentialId);
        if (cred == null || !cred.getApplicationId().equals(applicationId)) {
            throw new CranialException(ErrorCode.RESOURCE_NOT_FOUND, "Credential not found for application");
        }
        return cred;
    }

    private CredentialView toView(ApplicationCredentialEntity entity) {
        return new CredentialView(
                entity.getId(),
                entity.getApplicationId(),
                entity.getName(),
                entity.getEnvironment(),
                entity.getKeyPrefix(),
                entity.getStatus(),
                entity.getExpiresAt(),
                entity.getLastUsedAt(),
                entity.getRevokedAt(),
                entity.getCreatedAt()
        );
    }
}

