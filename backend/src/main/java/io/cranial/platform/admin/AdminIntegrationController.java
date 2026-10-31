package io.cranial.platform.admin;

import io.cranial.platform.application.capability.CapabilityEntitlementService;
import io.cranial.platform.application.capability.CapabilityEntitlementView;
import io.cranial.platform.application.credential.ApplicationSandboxBootstrapService;
import io.cranial.platform.application.registry.ApplicationEntity;
import io.cranial.platform.application.registry.ApplicationMapper;
import io.cranial.platform.orchestration.mapper.ToolMapper;
import io.cranial.platform.platform.common.ApiResponse;
import io.cranial.platform.platform.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/admin/v1/integrations")
@Tag(name = "Admin Integration Management", description = "Administration of application integrations, sandbox environments, client SDK parameters, and live gateway test diagnostics")
@SecurityRequirement(name = OpenApiConfig.SCHEME_ADMIN_JWT)
public class AdminIntegrationController {

    private final ApplicationSandboxBootstrapService sandboxService;
    private final ApplicationMapper applicationMapper;
    private final CapabilityEntitlementService entitlementService;
    private final ToolMapper toolMapper;

    public AdminIntegrationController(
            ApplicationSandboxBootstrapService sandboxService,
            ApplicationMapper applicationMapper,
            CapabilityEntitlementService entitlementService,
            ToolMapper toolMapper
    ) {
        this.sandboxService = sandboxService;
        this.applicationMapper = applicationMapper;
        this.entitlementService = entitlementService;
        this.toolMapper = toolMapper;
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('app:read')")
    @Operation(summary = "Get overview summary of all client application integrations")
    public ApiResponse<List<Map<String, Object>>> getIntegrationSummary() {
        List<ApplicationSandboxBootstrapService.SandboxCredentialInfo> sandboxes = sandboxService.getActiveSandboxCredentials();
        List<Map<String, Object>> list = new ArrayList<>();

        for (ApplicationSandboxBootstrapService.SandboxCredentialInfo sb : sandboxes) {
            ApplicationEntity app = applicationMapper.selectList(null).stream()
                    .filter(a -> a.getCode().equalsIgnoreCase(sb.applicationCode()))
                    .findFirst()
                    .orElse(null);

            List<CapabilityEntitlementView> entitlements = app != null
                    ? entitlementService.listApplicationEntitlements(app.getId())
                    : List.<CapabilityEntitlementView>of();
            var tools = app != null ? toolMapper.findAvailableForApplication(app.getId()) : List.of();

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("code", sb.applicationCode());
            item.put("name", sb.applicationName());
            item.put("environment", sb.environment());
            item.put("status", app != null ? app.getStatus() : "ACTIVE");
            item.put("sandbox_api_key", sb.apiKey());
            item.put("sandbox_signing_secret", sb.signingSecret());
            item.put("default_tenant_ref", sb.tenantRef());
            item.put("default_user_ref", sb.userRef());
            item.put("default_roles", sb.roles());
            item.put("default_scopes", sb.scopes());
            item.put("entitled_capabilities", entitlements.stream().filter(CapabilityEntitlementView::enabled).map(CapabilityEntitlementView::capabilityCode).toList());
            item.put("available_tools_count", tools.size());
            item.put("money_management_only", "peggy".equalsIgnoreCase(sb.applicationCode()));

            list.add(item);
        }

        return ApiResponse.ok(list);
    }

    @GetMapping("/sandbox")
    @PreAuthorize("hasAuthority('app:read')")
    @Operation(summary = "List sandbox credentials and copy-paste connection parameters")
    public ApiResponse<List<ApplicationSandboxBootstrapService.SandboxCredentialInfo>> listSandbox() {
        return ApiResponse.ok(sandboxService.getActiveSandboxCredentials());
    }
}

