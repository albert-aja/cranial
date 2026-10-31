package io.cranial.platform.admin;

import io.cranial.platform.application.featureflag.FeatureFlagService;
import io.cranial.platform.application.featureflag.FeatureFlagView;
import io.cranial.platform.platform.common.ApiResponse;
import io.cranial.platform.platform.config.OpenApiConfig;
import io.cranial.platform.security.AdminPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/v1/feature-flags")
@Tag(name = "Admin Feature Flags (v1)", description = "Manage platform capability rollouts and canary feature flags")
@SecurityRequirement(name = OpenApiConfig.SCHEME_ADMIN_JWT)
public class AdminFeatureFlagController {

    private final FeatureFlagService featureFlagService;

    public AdminFeatureFlagController(FeatureFlagService featureFlagService) {
        this.featureFlagService = featureFlagService;
    }

    public record UpdateFeatureFlagRequest(
            @NotNull Boolean enabled,
            @Min(0) @Max(100) Integer rolloutPercentage,
            List<String> allowedAppCodes
    ) {
    }

    @GetMapping
    @PreAuthorize("hasAuthority('app:read')")
    @Operation(summary = "List all platform feature flags and rollout rules")
    public ApiResponse<List<FeatureFlagView>> listFeatureFlags() {
        return ApiResponse.ok(featureFlagService.listFlags());
    }

    @PutMapping("/{flagKey}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Update feature flag status, canary percentage, or allowed applications")
    public ApiResponse<FeatureFlagView> updateFeatureFlag(
            @PathVariable String flagKey,
            @AuthenticationPrincipal AdminPrincipal operator,
            @Valid @RequestBody UpdateFeatureFlagRequest req,
            HttpServletRequest servletRequest
    ) {
        int percentage = req.rolloutPercentage() != null ? req.rolloutPercentage() : 100;
        return ApiResponse.ok(featureFlagService.updateFlag(
                flagKey,
                req.enabled(),
                percentage,
                req.allowedAppCodes(),
                operator.userId(),
                servletRequest.getRemoteAddr()
        ));
    }
}

