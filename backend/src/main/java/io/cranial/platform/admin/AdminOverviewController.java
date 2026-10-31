package io.cranial.platform.admin;

import io.cranial.platform.application.capability.CapabilityEntitlementService;
import io.cranial.platform.application.credential.ApplicationCredentialService;
import io.cranial.platform.application.registry.ApplicationRegistryService;
import io.cranial.platform.application.tenant.TenantService;
import io.cranial.platform.audit.AuditService;
import io.cranial.platform.platform.common.ApiResponse;
import io.cranial.platform.platform.config.CranialProperties;
import io.cranial.platform.platform.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/v1")
@Tag(name = "Admin Overview & Diagnostics (v1)", description = "Platform health, summary metrics, and non-secret configuration diagnostics")
@SecurityRequirement(name = OpenApiConfig.SCHEME_ADMIN_JWT)
public class AdminOverviewController {

    private final ApplicationRegistryService registryService;
    private final ApplicationCredentialService credentialService;
    private final CapabilityEntitlementService capabilityService;
    private final TenantService tenantService;
    private final AuditService auditService;
    private final CranialProperties properties;

    public AdminOverviewController(
            ApplicationRegistryService registryService,
            ApplicationCredentialService credentialService,
            CapabilityEntitlementService capabilityService,
            TenantService tenantService,
            AuditService auditService,
            CranialProperties properties
    ) {
        this.registryService = registryService;
        this.credentialService = credentialService;
        this.capabilityService = capabilityService;
        this.tenantService = tenantService;
        this.auditService = auditService;
        this.properties = properties;
    }

    @GetMapping("/overview")
    @PreAuthorize("hasAuthority('app:read')")
    @Operation(summary = "Platform foundation overview counters and governance posture")
    public ApiResponse<Map<String, Object>> overview() {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("platform", "CRANIAL");
        summary.put("category", "Universal AI Integration Platform");
        summary.put("tagline", "One Brain. Many Connections.");
        summary.put("environment", properties.environment());
        summary.put("phase", "Phase 1 — Platform Foundation");
        summary.put("applications_total", registryService.countTotal());
        summary.put("applications_active", registryService.countByStatus("ACTIVE"));
        summary.put("applications_suspended", registryService.countByStatus("SUSPENDED"));
        summary.put("credentials_active", credentialService.countActive());
        summary.put("capabilities_catalog_count", capabilityService.listCatalog().size());
        summary.put("capability_entitlements_enabled", capabilityService.countEnabledEntitlements());
        summary.put("tenants_active", tenantService.countActive());
        summary.put("audit_denied_events", auditService.countByOutcome("DENIED"));
        summary.put("governance", Map.of(
                "local_first_default", true,
                "automatic_paid_fallback", false,
                "hosted_providers_default_enabled", false
        ));
        summary.put("generated_at", Instant.now());
        return ApiResponse.ok(summary);
    }

    @GetMapping("/diagnostics")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Non-secret runtime and security configuration diagnostics")
    public ApiResponse<Map<String, Object>> diagnostics() {
        Map<String, Object> diag = new LinkedHashMap<>();
        diag.put("environment", properties.environment());
        diag.put("java_version", Runtime.version().toString());
        diag.put("admin_jwt_ttl_minutes", properties.security().adminJwtTtlMinutes());
        diag.put("context_max_skew_seconds", properties.security().contextMaxSkewSeconds());
        diag.put("admin_login_rpm", properties.security().adminLoginRpm());
        diag.put("api_key_pepper_configured", isNonBlank(properties.security().apiKeyPepper()));
        diag.put("encryption_key_configured", isNonBlank(properties.security().encryptionKey()));
        diag.put("admin_jwt_secret_configured", isNonBlank(properties.security().adminJwtSecret()));
        diag.put("cors_allowed_origins", properties.cors() != null ? properties.cors().allowedOrigins() : java.util.List.of());
        return ApiResponse.ok(diag);
    }

    private static boolean isNonBlank(String value) {
        return value != null && !value.isBlank();
    }
}

