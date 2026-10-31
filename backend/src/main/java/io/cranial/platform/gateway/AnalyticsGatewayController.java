package io.cranial.platform.gateway;

import io.cranial.platform.analytics.model.*;
import io.cranial.platform.analytics.service.AnalyticsIntelligenceService;
import io.cranial.platform.application.capability.CapabilityEntitlementService;
import io.cranial.platform.platform.common.ApiResponse;
import io.cranial.platform.platform.config.OpenApiConfig;
import io.cranial.platform.security.VerifiedRequestContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/analytics")
@Tag(name = "Data Intelligence & Analytics Gateway", description = "Deterministic reporting queries, trend analysis, statistical anomaly detection, forecasting, and natural language clarification")
@SecurityRequirement(name = OpenApiConfig.SCHEME_APP_API_KEY)
public class AnalyticsGatewayController {

    private static final String CAPABILITY_CODE = "data_intelligence";

    private final AnalyticsIntelligenceService analyticsService;
    private final CapabilityEntitlementService entitlementService;

    public AnalyticsGatewayController(
            AnalyticsIntelligenceService analyticsService,
            CapabilityEntitlementService entitlementService
    ) {
        this.analyticsService = analyticsService;
        this.entitlementService = entitlementService;
    }

    @GetMapping("/datasets")
    @Operation(summary = "List authorized application reporting datasets")
    public ApiResponse<List<AnalyticsDatasetView>> listDatasets(
            @AuthenticationPrincipal VerifiedRequestContext ctx,
            HttpServletRequest servletRequest
    ) {
        entitlementService.assertEntitled(ctx.applicationId(), CAPABILITY_CODE, servletRequest.getRemoteAddr());
        return ApiResponse.ok(analyticsService.listDatasets(ctx.applicationId(), ctx.tenantId(), false));
    }

    @PostMapping("/query")
    @Operation(summary = "Execute deterministic reporting query or natural language analytics question")
    public ApiResponse<AnalyticsReportResult> executeQuery(
            @AuthenticationPrincipal VerifiedRequestContext ctx,
            @Valid @RequestBody AnalyticsQueryRequest request,
            HttpServletRequest servletRequest
    ) {
        entitlementService.assertEntitled(ctx.applicationId(), CAPABILITY_CODE, servletRequest.getRemoteAddr());
        return ApiResponse.ok(analyticsService.executeQuery(request, ctx.applicationId(), ctx.tenantId(), false));
    }

    @GetMapping("/anomalies")
    @Operation(summary = "List detected statistical spending outliers and anomalies")
    public ApiResponse<List<AnomalyItemView>> listAnomalies(
            @AuthenticationPrincipal VerifiedRequestContext ctx,
            @RequestParam(value = "dataset_code", required = false) String datasetCode,
            @RequestParam(value = "status", required = false) String status,
            HttpServletRequest servletRequest
    ) {
        entitlementService.assertEntitled(ctx.applicationId(), CAPABILITY_CODE, servletRequest.getRemoteAddr());
        return ApiResponse.ok(analyticsService.listAnomalies(datasetCode, status, ctx.applicationId(), ctx.tenantId(), false));
    }

    @PostMapping("/anomalies/{id}/review")
    @Operation(summary = "Submit human review on detected anomaly (e.g. tag as false positive or verified anomaly)")
    public ApiResponse<AnomalyItemView> reviewAnomaly(
            @AuthenticationPrincipal VerifiedRequestContext ctx,
            @PathVariable UUID id,
            @Valid @RequestBody AnomalyReviewRequest request,
            HttpServletRequest servletRequest
    ) {
        entitlementService.assertEntitled(ctx.applicationId(), CAPABILITY_CODE, servletRequest.getRemoteAddr());
        return ApiResponse.ok(analyticsService.reviewAnomaly(id, request, ctx.applicationId(), ctx.tenantId(), false, ctx.applicationCode()));
    }
}
