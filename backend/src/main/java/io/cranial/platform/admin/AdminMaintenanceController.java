package io.cranial.platform.admin;

import io.cranial.platform.application.maintenance.PlatformMaintenanceService;
import io.cranial.platform.platform.common.ApiResponse;
import io.cranial.platform.platform.config.OpenApiConfig;
import io.cranial.platform.security.AdminPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/v1/maintenance")
@Tag(name = "Admin Maintenance (v1)", description = "Run and inspect platform background maintenance routines")
@SecurityRequirement(name = OpenApiConfig.SCHEME_ADMIN_JWT)
public class AdminMaintenanceController {

    private final PlatformMaintenanceService maintenanceService;

    public AdminMaintenanceController(PlatformMaintenanceService maintenanceService) {
        this.maintenanceService = maintenanceService;
    }

    @PostMapping("/run")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Manually trigger background maintenance to purge expired idempotency records and rotating credentials")
    public ApiResponse<PlatformMaintenanceService.MaintenanceSummary> runMaintenance(
            @AuthenticationPrincipal AdminPrincipal operator,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.ok(maintenanceService.runMaintenance(
                "MANUAL",
                operator.userId().toString(),
                servletRequest.getRemoteAddr()
        ));
    }
}

