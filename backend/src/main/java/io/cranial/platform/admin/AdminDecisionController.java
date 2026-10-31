package io.cranial.platform.admin;

import io.cranial.platform.decision.model.*;
import io.cranial.platform.decision.service.DecisionIntelligenceService;
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
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/v1/decision")
@Tag(name = "Admin Decision Console", description = "Administration of decision optimization plans, human approval review queue, feedback outcome telemetry, and deterministic solver benchmarks")
@SecurityRequirement(name = OpenApiConfig.SCHEME_ADMIN_JWT)
public class AdminDecisionController {

    private final DecisionIntelligenceService decisionService;

    public AdminDecisionController(DecisionIntelligenceService decisionService) {
        this.decisionService = decisionService;
    }

    @GetMapping("/plans")
    @PreAuthorize("hasAuthority('app:read')")
    @Operation(summary = "List all optimization plans and scenarios")
    public ApiResponse<List<DecisionPlanView>> listPlans(
            @RequestParam(value = "application_id", required = false) UUID applicationId,
            @RequestParam(value = "tenant_id", required = false) UUID tenantId
    ) {
        return ApiResponse.ok(decisionService.listPlans(applicationId, tenantId));
    }

    @PostMapping("/plans")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('capability:manage')")
    @Operation(summary = "Create new decision optimization plan")
    public ApiResponse<DecisionPlanView> createPlan(
            @Valid @RequestBody DecisionPlanRequest request
    ) {
        return ApiResponse.ok(decisionService.createPlan(request));
    }

    @GetMapping("/recommendations")
    @PreAuthorize("hasAuthority('app:read')")
    @Operation(summary = "List recommendations for operator inspection")
    public ApiResponse<List<DecisionRecommendationView>> listRecommendations(
            @RequestParam(value = "plan_code", required = false) String planCode,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "application_id", required = false) UUID applicationId
    ) {
        return ApiResponse.ok(decisionService.listRecommendations(planCode, status, applicationId));
    }

    @PostMapping("/recommendations")
    @PreAuthorize("hasAuthority('capability:manage')")
    @Operation(summary = "Generate optimization recommendation with trade-offs in operator console")
    public ApiResponse<DecisionRecommendationView> generateRecommendation(
            @Valid @RequestBody DecisionRecommendationRequest request
    ) {
        return ApiResponse.ok(decisionService.generateRecommendation(request, null));
    }

    @PostMapping("/recommendations/{id}/approve")
    @PreAuthorize("hasAuthority('capability:manage')")
    @Operation(summary = "Human approval or rejection for consequential recommendation")
    public ApiResponse<DecisionRecommendationView> reviewRecommendation(
            @PathVariable UUID id,
            @Valid @RequestBody RecommendationApprovalRequest request,
            @AuthenticationPrincipal AdminPrincipal admin
    ) {
        String reviewer = admin != null ? admin.email() : "operator";
        return ApiResponse.ok(decisionService.reviewRecommendation(id, request, reviewer));
    }

    @GetMapping("/recommendations/{id}/feedback")
    @PreAuthorize("hasAuthority('app:read')")
    @Operation(summary = "List feedback and outcome records for recommendation")
    public ApiResponse<List<DecisionFeedbackView>> listFeedbacks(
            @PathVariable UUID id
    ) {
        return ApiResponse.ok(decisionService.listFeedbacks(id));
    }

    @PostMapping("/recommendations/{id}/feedback")
    @PreAuthorize("hasAuthority('capability:manage')")
    @Operation(summary = "Record feedback and outcome telemetry for recommendation in operator console")
    public ApiResponse<DecisionFeedbackView> submitFeedback(
            @PathVariable UUID id,
            @Valid @RequestBody RecommendationFeedbackRequest request,
            @AuthenticationPrincipal AdminPrincipal admin
    ) {
        String reviewer = admin != null ? admin.email() : "operator";
        return ApiResponse.ok(decisionService.submitFeedback(id, request, null, reviewer));
    }

    @PostMapping("/benchmarks/run")
    @PreAuthorize("hasAuthority('capability:manage')")
    @Operation(summary = "Execute deterministic optimization solver benchmarks")
    public ApiResponse<DecisionBenchmarkResult> runBenchmarks() {
        return ApiResponse.ok(decisionService.runBenchmarks());
    }
}
