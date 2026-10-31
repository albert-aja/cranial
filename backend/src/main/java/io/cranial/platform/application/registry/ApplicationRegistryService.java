package io.cranial.platform.application.registry;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cranial.platform.audit.AuditEvent;
import io.cranial.platform.audit.AuditService;
import io.cranial.platform.platform.common.ApiResponse;
import io.cranial.platform.platform.common.PageMeta;
import io.cranial.platform.platform.crypto.CryptoService;
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
import java.util.regex.Pattern;

@Service
public class ApplicationRegistryService {

    private static final Pattern APP_CODE_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9\\-_]{1,78}[a-z0-9]$");
    private static final Set<String> VALID_ENVIRONMENTS = Set.of("DEV", "STAGING", "PROD");
    private static final Set<String> VALID_STATUSES = Set.of("ACTIVE", "SUSPENDED", "RETIRED");
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    private final ApplicationMapper applicationMapper;
    private final ApplicationSettingsMapper settingsMapper;
    private final CryptoService cryptoService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public ApplicationRegistryService(
            ApplicationMapper applicationMapper,
            ApplicationSettingsMapper settingsMapper,
            CryptoService cryptoService,
            AuditService auditService,
            ObjectMapper objectMapper
    ) {
        this.applicationMapper = applicationMapper;
        this.settingsMapper = settingsMapper;
        this.cryptoService = cryptoService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    public record CreateApplicationCommand(
            String code,
            String environment,
            String name,
            String description,
            List<String> allowedOrigins,
            Integer rateLimitRpm,
            Integer concurrencyLimit,
            Integer maxInputBytes,
            Boolean requireSignedContext
    ) {
    }

    public record UpdateApplicationSettingsCommand(
            String name,
            String description,
            List<String> allowedOrigins,
            Integer rateLimitRpm,
            Integer concurrencyLimit,
            Integer maxInputBytes,
            Boolean requireSignedContext,
            Boolean allowHostedProviders,
            Boolean allowSensitiveExternal,
            String webhookUrl,
            String webhookSecret
    ) {
    }

    @Transactional
    public ApplicationView createApplication(CreateApplicationCommand cmd, UUID actorUserId, String ipAddress) {
        String normalizedCode = cmd.code() == null ? "" : cmd.code().trim().toLowerCase(Locale.ROOT);
        if (!APP_CODE_PATTERN.matcher(normalizedCode).matches()) {
            throw new CranialException(ErrorCode.VALIDATION_ERROR, "Application code must be 3-80 lowercase alphanumeric, hyphen, or underscore characters");
        }
        String env = normalizeEnvironment(cmd.environment());

        Long existing = applicationMapper.selectCount(new LambdaQueryWrapper<ApplicationEntity>()
                .eq(ApplicationEntity::getCode, normalizedCode)
                .eq(ApplicationEntity::getEnvironment, env));
        if (existing != null && existing > 0) {
            throw new CranialException(ErrorCode.CONFLICT, "Application '" + normalizedCode + "' already exists in environment " + env);
        }

        Instant now = Instant.now();
        UUID appId = UUID.randomUUID();

        ApplicationEntity app = new ApplicationEntity();
        app.setId(appId);
        app.setCode(normalizedCode);
        app.setEnvironment(env);
        app.setName(cmd.name().trim());
        app.setDescription(cmd.description());
        app.setStatus("ACTIVE");
        app.setAllowedOrigins(toJson(cmd.allowedOrigins() != null ? cmd.allowedOrigins() : List.of()));
        app.setCreatedBy(actorUserId);
        app.setCreatedAt(now);
        app.setUpdatedAt(now);
        applicationMapper.insert(app);

        ApplicationSettingsEntity settings = new ApplicationSettingsEntity();
        settings.setApplicationId(appId);
        settings.setRateLimitRpm(cmd.rateLimitRpm() != null ? Math.max(1, cmd.rateLimitRpm()) : 120);
        settings.setConcurrencyLimit(cmd.concurrencyLimit() != null ? Math.max(1, cmd.concurrencyLimit()) : 10);
        settings.setMaxInputBytes(cmd.maxInputBytes() != null ? Math.max(1024, cmd.maxInputBytes()) : 1_048_576);
        settings.setRequireSignedContext(Boolean.TRUE.equals(cmd.requireSignedContext()));
        // Local-first policy: hosted/paid providers and external sensitive-data sharing disabled by default
        settings.setAllowHostedProviders(false);
        settings.setAllowSensitiveExternal(false);
        settings.setConfigJson("{}");
        settings.setUpdatedAt(now);
        settingsMapper.insert(settings);

        auditService.record(new AuditEvent(
                "PLATFORM_USER",
                actorUserId != null ? actorUserId.toString() : "system",
                appId,
                null,
                "APPLICATION_REGISTERED",
                "APPLICATION",
                appId.toString(),
                "SUCCESS",
                Map.of("code", normalizedCode, "environment", env),
                ipAddress
        ));

        return toView(app, settings);
    }

    @Transactional
    public ApplicationView updateStatus(UUID applicationId, String newStatus, UUID actorUserId, String ipAddress) {
        String normalized = newStatus == null ? "" : newStatus.trim().toUpperCase(Locale.ROOT);
        if (!VALID_STATUSES.contains(normalized)) {
            throw new CranialException(ErrorCode.VALIDATION_ERROR, "Invalid application status: " + newStatus);
        }
        ApplicationEntity app = getRequiredEntity(applicationId);
        String previous = app.getStatus();
        app.setStatus(normalized);
        app.setUpdatedAt(Instant.now());
        applicationMapper.updateById(app);

        ApplicationSettingsEntity settings = getRequiredSettings(applicationId);

        auditService.record(new AuditEvent(
                "PLATFORM_USER",
                actorUserId != null ? actorUserId.toString() : "system",
                applicationId,
                null,
                "APPLICATION_STATUS_UPDATED",
                "APPLICATION",
                applicationId.toString(),
                "SUCCESS",
                Map.of("previous_status", previous, "new_status", normalized),
                ipAddress
        ));

        return toView(app, settings);
    }

    @Transactional
    public ApplicationView updateSettings(
            UUID applicationId,
            UpdateApplicationSettingsCommand cmd,
            UUID actorUserId,
            String ipAddress
    ) {
        ApplicationEntity app = getRequiredEntity(applicationId);
        ApplicationSettingsEntity settings = getRequiredSettings(applicationId);
        Instant now = Instant.now();

        if (cmd.name() != null && !cmd.name().isBlank()) {
            app.setName(cmd.name().trim());
        }
        if (cmd.description() != null) {
            app.setDescription(cmd.description());
        }
        if (cmd.allowedOrigins() != null) {
            app.setAllowedOrigins(toJson(cmd.allowedOrigins()));
        }
        app.setUpdatedAt(now);
        applicationMapper.updateById(app);

        if (cmd.rateLimitRpm() != null) {
            if (cmd.rateLimitRpm() <= 0) {
                throw new CranialException(ErrorCode.VALIDATION_ERROR, "rate_limit_rpm must be positive");
            }
            settings.setRateLimitRpm(cmd.rateLimitRpm());
        }
        if (cmd.concurrencyLimit() != null) {
            if (cmd.concurrencyLimit() <= 0) {
                throw new CranialException(ErrorCode.VALIDATION_ERROR, "concurrency_limit must be positive");
            }
            settings.setConcurrencyLimit(cmd.concurrencyLimit());
        }
        if (cmd.maxInputBytes() != null) {
            if (cmd.maxInputBytes() < 1024) {
                throw new CranialException(ErrorCode.VALIDATION_ERROR, "max_input_bytes must be at least 1024");
            }
            settings.setMaxInputBytes(cmd.maxInputBytes());
        }
        if (cmd.requireSignedContext() != null) {
            settings.setRequireSignedContext(cmd.requireSignedContext());
        }
        if (cmd.allowHostedProviders() != null) {
            settings.setAllowHostedProviders(cmd.allowHostedProviders());
        }
        if (cmd.allowSensitiveExternal() != null) {
            if (Boolean.TRUE.equals(cmd.allowSensitiveExternal()) && !Boolean.TRUE.equals(settings.getAllowHostedProviders())) {
                throw new CranialException(ErrorCode.VALIDATION_ERROR, "Cannot enable allow_sensitive_external while allow_hosted_providers is disabled");
            }
            settings.setAllowSensitiveExternal(cmd.allowSensitiveExternal());
        }
        if (!Boolean.TRUE.equals(settings.getAllowHostedProviders())) {
            settings.setAllowSensitiveExternal(false);
        }
        if (cmd.webhookUrl() != null) {
            settings.setWebhookUrl(cmd.webhookUrl().isBlank() ? null : cmd.webhookUrl().trim());
        }
        if (cmd.webhookSecret() != null && !cmd.webhookSecret().isBlank()) {
            settings.setWebhookSecretEncrypted(cryptoService.encryptSecret(cmd.webhookSecret()));
        }
        settings.setUpdatedAt(now);
        settingsMapper.updateById(settings);

        auditService.record(new AuditEvent(
                "PLATFORM_USER",
                actorUserId != null ? actorUserId.toString() : "system",
                applicationId,
                null,
                "APPLICATION_SETTINGS_UPDATED",
                "APPLICATION",
                applicationId.toString(),
                "SUCCESS",
                Map.of(
                        "rate_limit_rpm", settings.getRateLimitRpm(),
                        "require_signed_context", settings.getRequireSignedContext(),
                        "allow_hosted_providers", settings.getAllowHostedProviders(),
                        "allow_sensitive_external", settings.getAllowSensitiveExternal()
                ),
                ipAddress
        ));

        return toView(app, settings);
    }

    public ApplicationView getApplication(UUID applicationId) {
        ApplicationEntity app = getRequiredEntity(applicationId);
        ApplicationSettingsEntity settings = getRequiredSettings(applicationId);
        return toView(app, settings);
    }

    public ApiResponse<List<ApplicationView>> listApplications(
            String environment,
            String status,
            String search,
            int page,
            int size
    ) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, size));

        LambdaQueryWrapper<ApplicationEntity> query = new LambdaQueryWrapper<>();
        if (environment != null && !environment.isBlank()) {
            query.eq(ApplicationEntity::getEnvironment, environment.trim().toUpperCase(Locale.ROOT));
        }
        if (status != null && !status.isBlank()) {
            query.eq(ApplicationEntity::getStatus, status.trim().toUpperCase(Locale.ROOT));
        }
        if (search != null && !search.isBlank()) {
            String term = search.trim();
            query.and(q -> q.like(ApplicationEntity::getCode, term.toLowerCase(Locale.ROOT))
                    .or()
                    .like(ApplicationEntity::getName, term));
        }
        query.orderByDesc(ApplicationEntity::getCreatedAt);

        Page<ApplicationEntity> result = applicationMapper.selectPage(new Page<>(safePage, safeSize), query);
        List<ApplicationView> views = result.getRecords().stream()
                .map(app -> toView(app, getRequiredSettings(app.getId())))
                .toList();
        return ApiResponse.paged(views, PageMeta.of(safePage, safeSize, result.getTotal()));
    }

    public ApplicationEntity getRequiredEntity(UUID applicationId) {
        ApplicationEntity app = applicationMapper.selectById(applicationId);
        if (app == null) {
            throw new CranialException(ErrorCode.RESOURCE_NOT_FOUND, "Application not found: " + applicationId);
        }
        return app;
    }

    public ApplicationSettingsEntity getRequiredSettings(UUID applicationId) {
        ApplicationSettingsEntity settings = settingsMapper.selectById(applicationId);
        if (settings == null) {
            throw new CranialException(ErrorCode.RESOURCE_NOT_FOUND, "Application settings not found: " + applicationId);
        }
        return settings;
    }

    public long countTotal() {
        return applicationMapper.selectCount(null);
    }

    public long countByStatus(String status) {
        return applicationMapper.selectCount(new LambdaQueryWrapper<ApplicationEntity>()
                .eq(ApplicationEntity::getStatus, status));
    }

    private static String normalizeEnvironment(String env) {
        String normalized = env == null ? "" : env.trim().toUpperCase(Locale.ROOT);
        if (!VALID_ENVIRONMENTS.contains(normalized)) {
            throw new CranialException(ErrorCode.VALIDATION_ERROR, "Environment must be one of DEV, STAGING, PROD");
        }
        return normalized;
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception ex) {
            return "[]";
        }
    }

    private List<String> parseOrigins(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, STRING_LIST);
        } catch (Exception ex) {
            return List.of();
        }
    }

    private ApplicationView toView(ApplicationEntity app, ApplicationSettingsEntity settings) {
        return new ApplicationView(
                app.getId(),
                app.getCode(),
                app.getEnvironment(),
                app.getName(),
                app.getDescription(),
                app.getStatus(),
                parseOrigins(app.getAllowedOrigins()),
                new ApplicationView.SettingsSummary(
                        settings.getRateLimitRpm(),
                        settings.getConcurrencyLimit(),
                        settings.getMaxInputBytes(),
                        Boolean.TRUE.equals(settings.getRequireSignedContext()),
                        Boolean.TRUE.equals(settings.getAllowHostedProviders()),
                        Boolean.TRUE.equals(settings.getAllowSensitiveExternal()),
                        settings.getWebhookUrl(),
                        settings.getWebhookSecretEncrypted() != null && !settings.getWebhookSecretEncrypted().isBlank()
                ),
                app.getCreatedAt(),
                app.getUpdatedAt()
        );
    }
}

