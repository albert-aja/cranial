package io.cranial.platform.admin;

import io.cranial.platform.platform.common.ApiResponse;
import io.cranial.platform.platform.config.OpenApiConfig;
import io.cranial.platform.provider.model.UsageRecordView;
import io.cranial.platform.provider.model.UsageSummaryView;
import io.cranial.platform.provider.service.UsageTrackingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/v1/usage")
@Tag(name = "Admin AI Usage & Cost Analytics", description = "Monitor tokens, costs, latency, and blocked fallbacks")
@SecurityRequirement(name = OpenApiConfig.SCHEME_ADMIN_JWT)
public class AdminUsageController {

    private final UsageTrackingService usageService;

    public AdminUsageController(UsageTrackingService usageService) {
        this.usageService = usageService;
    }

    @GetMapping("/summary")
    @Operation(summary = "Get aggregated AI usage and cost statistics")
    public ApiResponse<UsageSummaryView> getSummary(
            @RequestParam(value = "application_id", required = false) UUID applicationId
    ) {
        return ApiResponse.ok(usageService.getSummary(applicationId));
    }

    @GetMapping("/records")
    @Operation(summary = "List recent raw AI usage records")
    public ApiResponse<List<UsageRecordView>> listRecords(
            @RequestParam(value = "application_id", required = false) UUID applicationId,
            @RequestParam(value = "limit", defaultValue = "50") int limit
    ) {
        return ApiResponse.ok(usageService.listRecent(applicationId, Math.min(limit, 200)));
    }
}

