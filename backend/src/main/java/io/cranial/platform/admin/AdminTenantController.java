package io.cranial.platform.admin;

import io.cranial.platform.application.tenant.TenantMembershipEntity;
import io.cranial.platform.application.tenant.TenantService;
import io.cranial.platform.application.tenant.TenantView;
import io.cranial.platform.platform.common.ApiResponse;
import io.cranial.platform.platform.config.OpenApiConfig;
import io.cranial.platform.security.AdminPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/v1/tenants")
@Tag(name = "Admin Tenant Management (v1)", description = "Provision and isolate application-scoped tenants and memberships")
@SecurityRequirement(name = OpenApiConfig.SCHEME_ADMIN_JWT)
public class AdminTenantController {

    private final TenantService tenantService;

    public AdminTenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    public record ProvisionTenantRequest(
            @NotNull UUID applicationId,
            @NotBlank @Size(max = 150) String externalTenantRef,
            @NotBlank @Size(max = 200) String name,
            Map<String, Object> metadata
    ) {
    }

    public record UpdateTenantStatusRequest(@NotBlank String status) {
    }

    public record UpsertMembershipRequest(
            @NotBlank @Size(max = 150) String externalUserRef,
            @Size(max = 80) String memberRole,
            String status
    ) {
    }

    @GetMapping
    @PreAuthorize("hasAuthority('tenant:read')")
    @Operation(summary = "List provisioned tenants with optional application and status filters")
    public ApiResponse<List<TenantView>> listTenants(
            @RequestParam(required = false) UUID applicationId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return tenantService.listTenants(applicationId, status, page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('tenant:manage')")
    @Operation(summary = "Provision a new application-scoped tenant")
    public ApiResponse<TenantView> provisionTenant(
            @AuthenticationPrincipal AdminPrincipal operator,
            @Valid @RequestBody ProvisionTenantRequest req,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.ok(tenantService.provisionTenant(
                req.applicationId(),
                req.externalTenantRef(),
                req.name(),
                req.metadata(),
                operator.userId(),
                servletRequest.getRemoteAddr()
        ));
    }

    @PatchMapping("/{tenantId}/status")
    @PreAuthorize("hasAuthority('tenant:manage')")
    @Operation(summary = "Update tenant status (ACTIVE or SUSPENDED)")
    public ApiResponse<TenantView> updateTenantStatus(
            @PathVariable UUID tenantId,
            @AuthenticationPrincipal AdminPrincipal operator,
            @Valid @RequestBody UpdateTenantStatusRequest req,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.ok(tenantService.updateStatus(
                tenantId, req.status(), operator.userId(), servletRequest.getRemoteAddr()
        ));
    }

    @GetMapping("/{tenantId}/memberships")
    @PreAuthorize("hasAuthority('tenant:read')")
    @Operation(summary = "List external user memberships mapped to a tenant")
    public ApiResponse<List<TenantMembershipEntity>> listMemberships(@PathVariable UUID tenantId) {
        return ApiResponse.ok(tenantService.listMemberships(tenantId));
    }

    @PutMapping("/{tenantId}/memberships")
    @PreAuthorize("hasAuthority('tenant:manage')")
    @Operation(summary = "Create or update an external user membership in a tenant")
    public ApiResponse<TenantMembershipEntity> upsertMembership(
            @PathVariable UUID tenantId,
            @AuthenticationPrincipal AdminPrincipal operator,
            @Valid @RequestBody UpsertMembershipRequest req,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.ok(tenantService.upsertMembership(
                tenantId,
                req.externalUserRef(),
                req.memberRole(),
                req.status(),
                operator.userId(),
                servletRequest.getRemoteAddr()
        ));
    }
}

