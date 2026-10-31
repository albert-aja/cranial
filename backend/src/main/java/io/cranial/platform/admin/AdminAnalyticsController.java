package io.cranial.platform.admin;

import io.cranial.platform.analytics.engine.AnalyticsCacheManager;
import io.cranial.platform.analytics.model.*;
import io.cranial.platform.analytics.service.AnalyticsIntelligenceService;
import io.cranial.platform.platform.common.ApiResponse;
import io.cranial.platform.platform.config.OpenApiConfig;
import io.cranial.platform.security.AdminPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/v1/analytics")
@Tag(name = "Admin Analytics Console", description = "Administration of reporting datasets, deterministic query execution, anomaly review queue, query cache, and analytical benchmarks")
@SecurityRequirement(name = OpenApiConfig.SCHEME_ADMIN_JWT)
public class AdminAnalyticsController {

    private final AnalyticsIntelligenceService analyticsService;
    private final AnalyticsCacheManager cacheManager;

    public AdminAnalyticsController(
            AnalyticsIntelligenceService analyticsService,
            AnalyticsCacheManager cacheManager
    ) {
        this.analyticsService = analyticsService;
        this.cacheManager = cacheManager;
    }

    @GetMapping("/datasets")
    @PreAuthorize("hasAuthority('app:read')")
    @Operation(summary = "List all registered reporting datasets")
    public ApiResponse<List<AnalyticsDatasetView>> listDatasets(
            @RequestParam(value = "application_id", required = false) UUID applicationId,
            @RequestParam(value = "tenant_id", required = false) UUID tenantId
    ) {
        return ApiResponse.ok(analyticsService.listDatasets(applicationId, tenantId, true));
    }

    @PostMapping("/datasets")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('app:write') or hasAuthority('capability:manage')")
    @Operation(summary = "Register or update an application reporting dataset connector")
    public ApiResponse<AnalyticsDatasetView> saveDataset(
            @Valid @RequestBody AnalyticsDatasetRequest request
    ) {
        return ApiResponse.ok(analyticsService.saveDataset(request, request.applicationId(), request.tenantId(), true));
    }

    @PostMapping("/query")
    @PreAuthorize("hasAuthority('app:read')")
    @Operation(summary = "Execute deterministic reporting query or natural-language analysis in operator console")
    public ApiResponse<AnalyticsReportResult> executeQuery(
            @Valid @RequestBody AnalyticsQueryRequest request
    ) {
        return ApiResponse.ok(analyticsService.executeQuery(request, null, null, true));
    }

    @GetMapping("/anomalies")
    @PreAuthorize("hasAuthority('app:read')")
    @Operation(summary = "List detected statistical anomalies and outliers in operator review queue")
    public ApiResponse<List<AnomalyItemView>> listAnomalies(
            @RequestParam(value = "dataset_code", required = false) String datasetCode,
            @RequestParam(value = "status", required = false) String status
    ) {
        return ApiResponse.ok(analyticsService.listAnomalies(datasetCode, status, null, null, true));
    }

    @PostMapping("/anomalies/{id}/review")
    @PreAuthorize("hasAuthority('app:write') or hasAuthority('capability:manage')")
    @Operation(summary = "Submit operator review decision on anomaly (e.g., mark verified anomaly or false positive)")
    public ApiResponse<AnomalyItemView> reviewAnomaly(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody AnomalyReviewRequest request
    ) {
        return ApiResponse.ok(analyticsService.reviewAnomaly(id, request, null, null, true, principal.email()));
    }

    @GetMapping("/cache/stats")
    @PreAuthorize("hasAuthority('app:read')")
    @Operation(summary = "Retrieve query cache statistics")
    public ApiResponse<Map<String, Object>> getCacheStats() {
        return ApiResponse.ok(cacheManager.getCacheStats());
    }

    @PostMapping("/cache/purge")
    @PreAuthorize("hasAuthority('app:write') or hasAuthority('capability:manage')")
    @Operation(summary = "Purge cached analytical results")
    public ApiResponse<Map<String, Object>> purgeCache(
            @RequestParam(value = "dataset_code", required = false) String datasetCode
    ) {
        int purged = cacheManager.purgeCache(datasetCode, null);
        return ApiResponse.ok(Map.of("purged_entries", purged, "dataset_code", datasetCode != null ? datasetCode : "ALL"));
    }

    @PostMapping("/benchmarks/run")
    @PreAuthorize("hasAuthority('app:read')")
    @Operation(summary = "Run deterministic analytics regression and anomaly detection benchmark suite")
    public ApiResponse<AnalyticsBenchmarkResult> runBenchmarks() {
        return ApiResponse.ok(analyticsService.runEvaluationBenchmarks());
    }
}
