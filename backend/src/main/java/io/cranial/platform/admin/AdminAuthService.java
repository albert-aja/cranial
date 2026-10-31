package io.cranial.platform.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.cranial.platform.audit.AuditEvent;
import io.cranial.platform.audit.AuditService;
import io.cranial.platform.platform.config.CranialProperties;
import io.cranial.platform.platform.exception.CranialException;
import io.cranial.platform.platform.exception.ErrorCode;
import io.cranial.platform.platform.observability.PlatformMetrics;
import io.cranial.platform.platform.ratelimit.RateLimitDecision;
import io.cranial.platform.platform.ratelimit.RateLimiter;
import io.cranial.platform.security.AdminPrincipal;
import io.cranial.platform.security.JwtTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class AdminAuthService {

    private static final Logger log = LoggerFactory.getLogger(AdminAuthService.class);

    private final PlatformUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final RateLimiter rateLimiter;
    private final CranialProperties properties;
    private final AuditService auditService;
    private final PlatformMetrics metrics;

    public AdminAuthService(
            PlatformUserMapper userMapper,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService,
            RateLimiter rateLimiter,
            CranialProperties properties,
            AuditService auditService,
            PlatformMetrics metrics
    ) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.auditService = auditService;
        this.metrics = metrics;
    }

    public record LoginResponse(
            String accessToken,
            Instant expiresAt,
            AdminPrincipal operator
    ) {
    }

    @Transactional
    public LoginResponse login(String email, String password, String ipAddress) {
        String safeIp = ipAddress != null ? ipAddress : "unknown";
        int loginRpm = Math.max(3, properties.security().adminLoginRpm());
        RateLimitDecision rl = rateLimiter.tryAcquire("admin_login:" + safeIp, loginRpm);
        if (!rl.allowed()) {
            metrics.recordRateLimitRejection("admin_login");
            throw new CranialException(
                    ErrorCode.RATE_LIMIT_EXCEEDED,
                    "Too many login attempts. Retry later.",
                    rl.retryAfterSeconds()
            );
        }

        String normalizedEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        PlatformUserEntity user = userMapper.selectOne(
                new LambdaQueryWrapper<PlatformUserEntity>().eq(PlatformUserEntity::getEmail, normalizedEmail)
        );

        if (user == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            metrics.recordAuthDecision("admin_login", "denied_bad_credentials");
            auditService.record(new AuditEvent(
                    "ANONYMOUS",
                    normalizedEmail,
                    null,
                    null,
                    "ADMIN_LOGIN_FAILED",
                    "PLATFORM_USER",
                    normalizedEmail,
                    "DENIED",
                    Map.of("reason", "INVALID_CREDENTIALS"),
                    safeIp
            ));
            throw new CranialException(ErrorCode.INVALID_CREDENTIALS, "Invalid email or password");
        }

        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            metrics.recordAuthDecision("admin_login", "denied_inactive_user");
            auditService.record(new AuditEvent(
                    "PLATFORM_USER",
                    user.getId().toString(),
                    null,
                    null,
                    "ADMIN_LOGIN_DENIED",
                    "PLATFORM_USER",
                    user.getId().toString(),
                    "DENIED",
                    Map.of("status", user.getStatus()),
                    safeIp
            ));
            throw new CranialException(ErrorCode.FORBIDDEN, "Operator account is not active");
        }

        List<String> roles = userMapper.findRoleCodesByUserId(user.getId());
        List<String> permissions = userMapper.findPermissionCodesByUserId(user.getId());

        Instant now = Instant.now();
        user.setLastLoginAt(now);
        user.setUpdatedAt(now);
        userMapper.updateById(user);

        AdminPrincipal principal = new AdminPrincipal(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                roles,
                permissions
        );
        JwtTokenService.IssuedAdminToken token = jwtTokenService.issueToken(principal);

        metrics.recordAuthDecision("admin_login", "success");
        auditService.record(new AuditEvent(
                "PLATFORM_USER",
                user.getId().toString(),
                null,
                null,
                "ADMIN_LOGIN_SUCCESS",
                "PLATFORM_USER",
                user.getId().toString(),
                "SUCCESS",
                Map.of("email", user.getEmail(), "roles", roles),
                safeIp
        ));

        return new LoginResponse(token.accessToken(), token.expiresAt(), principal);
    }

    @Transactional
    public PlatformUserEntity createOperatorIfNotExists(
            String email,
            String rawPassword,
            String displayName,
            String roleCode
    ) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        PlatformUserEntity existing = userMapper.selectOne(
                new LambdaQueryWrapper<PlatformUserEntity>().eq(PlatformUserEntity::getEmail, normalizedEmail)
        );
        if (existing != null) {
            return existing;
        }

        UUID roleId = userMapper.findRoleIdByCode(roleCode);
        if (roleId == null) {
            throw new CranialException(ErrorCode.RESOURCE_NOT_FOUND, "Role not found: " + roleCode);
        }

        Instant now = Instant.now();
        PlatformUserEntity entity = new PlatformUserEntity();
        entity.setId(UUID.randomUUID());
        entity.setEmail(normalizedEmail);
        entity.setPasswordHash(passwordEncoder.encode(rawPassword));
        entity.setDisplayName(displayName != null && !displayName.isBlank() ? displayName.trim() : "Operator");
        entity.setStatus("ACTIVE");
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        userMapper.insert(entity);
        userMapper.assignRole(entity.getId(), roleId);
        return entity;
    }

    @Transactional
    public void bootstrapInitialAdminIfEmpty() {
        Long count = userMapper.selectCount(null);
        if (count != null && count > 0) {
            return;
        }
        CranialProperties.BootstrapProperties boot = properties.bootstrap();
        if (boot == null || boot.adminEmail() == null || boot.adminEmail().isBlank()
                || boot.adminPassword() == null || boot.adminPassword().isBlank()) {
            log.info("No platform_users found and CRANIAL_BOOTSTRAP_ADMIN_EMAIL/PASSWORD not set; skipping bootstrap seeding.");
            return;
        }
        PlatformUserEntity created = createOperatorIfNotExists(
                boot.adminEmail(),
                boot.adminPassword(),
                boot.adminName(),
                "SUPER_ADMIN"
        );
        log.info("Bootstrapped initial SUPER_ADMIN operator email={} id={}", created.getEmail(), created.getId());
    }
}

