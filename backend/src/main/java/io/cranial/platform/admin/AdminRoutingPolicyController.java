package io.cranial.platform.admin;

import io.cranial.platform.platform.common.ApiResponse;
import io.cranial.platform.platform.config.OpenApiConfig;
import io.cranial.platform.provider.model.RoutingPolicyView;
import io.cranial.platform.provider.model.SimulationRequest;
import io.cranial.platform.provider.model.SimulationResult;
import io.cranial.platform.provider.routing.ModelRouter;
import io.cranial.platform.provider.service.ProviderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/v1/routing-policies")
@Tag(name = "Admin Routing Policies", description = "Manage local-first model routing rules and simulate decisions")
@SecurityRequirement(name = OpenApiConfig.SCHEME_ADMIN_JWT)
public class AdminRoutingPolicyController {

    private final ProviderService providerService;
    private final ModelRouter modelRouter;

    public AdminRoutingPolicyController(ProviderService providerService, ModelRouter modelRouter) {
        this.providerService = providerService;
        this.modelRouter = modelRouter;
    }

    @GetMapping
    @Operation(summary = "List all routing policies")
    public ApiResponse<List<RoutingPolicyView>> listPolicies() {
        return ApiResponse.ok(providerService.listRoutingPolicies());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'PLATFORM_OPERATOR')")
    @Operation(summary = "Create or update routing policy")
    public ApiResponse<RoutingPolicyView> savePolicy(@Valid @RequestBody SaveRoutingPolicyRequest req) {
        RoutingPolicyView view = providerService.saveRoutingPolicy(
                req.id(),
                req.name(),
                req.capabilityCode(),
                req.taskCode(),
                req.preferredModelId(),
                req.fallbackModelId(),
                req.allowedDataSensitivity(),
                req.isActive() == null || req.isActive()
        );
        return ApiResponse.ok(view);
    }

    @PostMapping("/simulate")
    @Operation(summary = "Simulate routing decision and test fallback governance against an application")
    public ApiResponse<SimulationResult> simulateRouting(@Valid @RequestBody SimulationRequest req) {
        return ApiResponse.ok(modelRouter.simulateRouting(req));
    }

    public record SaveRoutingPolicyRequest(
            UUID id,
            @NotBlank String name,
            @NotBlank @com.fasterxml.jackson.annotation.JsonAlias({"capabilityCode", "capability_code"}) String capabilityCode,
            @com.fasterxml.jackson.annotation.JsonAlias({"taskCode", "task_code"}) String taskCode,
            @NotNull @com.fasterxml.jackson.annotation.JsonAlias({"preferredModelId", "preferred_model_id"}) UUID preferredModelId,
            @com.fasterxml.jackson.annotation.JsonAlias({"fallbackModelId", "fallback_model_id"}) UUID fallbackModelId,
            @com.fasterxml.jackson.annotation.JsonAlias({"allowedDataSensitivity", "allowed_data_sensitivity"}) String allowedDataSensitivity,
            @com.fasterxml.jackson.annotation.JsonAlias({"isActive", "is_active"}) Boolean isActive
    ) {
    }
}
