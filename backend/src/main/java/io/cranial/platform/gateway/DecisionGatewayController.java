package io.cranial.platform.gateway;

import io.cranial.platform.application.capability.CapabilityEntitlementService;
import io.cranial.platform.decision.model.*;
import io.cranial.platform.decision.service.DecisionIntelligenceService;
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
@RequestMapping("/api/v1/decision")
@Tag(name = "Decision Intelligence & Optimization Gateway", description = "Deterministic financial optimization, debt avalanche/snowball, constraint validation, scenario comparison, and simulation recommendations")
@SecurityRequirement(name = OpenApiConfig.SCHEME_APP_API_KEY)
public class DecisionGatewayController {

    private static final String CAPABILITY_CODE = "decision_intelligence";

    private final DecisionIntelligenceService decisionService;
    private final CapabilityEntitlementService entitlementService;

    public DecisionGatewayController(
            DecisionIntelligenceService decisionService,
            CapabilityEntitlementService entitlementService
    ) {
        this.decisionService = decisionService;
        this.entitlementService = entitlementService;
    }

    @GetMapping("/plans")
    @Operation(summary = "List active optimization plans and scenarios")
    public ApiResponse<List<DecisionPlanView>> listPlans(
            @AuthenticationPrincipal VerifiedRequestContext ctx,
            HttpServletRequest servletRequest
    ) {
        entitlementService.assertEntitled(ctx.applicationId(), CAPABILITY_CODE, servletRequest.getRemoteAddr());
        return ApiResponse.ok(decisionService.listPlans(ctx.applicationId(), ctx.tenantId()));
    }

    @PostMapping("/recommendations")
    @Operation(summary = "Generate optimization recommendation with trade-offs and constraint validation")
    public ApiResponse<DecisionRecommendationView> generateRecommendation(
            @AuthenticationPrincipal VerifiedRequestContext ctx,
            @Valid @RequestBody DecisionRecommendationRequest request,
            HttpServletRequest servletRequest
    ) {
        entitlementService.assertEntitled(ctx.applicationId(), CAPABILITY_CODE, servletRequest.getRemoteAddr());
        return ApiResponse.ok(decisionService.generateRecommendation(request, ctx));
    }

    @GetMapping("/recommendations")
    @Operation(summary = "List recommendations generated for application")
    public ApiResponse<List<DecisionRecommendationView>> listRecommendations(
            @AuthenticationPrincipal VerifiedRequestContext ctx,
            @RequestParam(required = false) String plan_code,
            @RequestParam(required = false) String status,
            HttpServletRequest servletRequest
    ) {
        entitlementService.assertEntitled(ctx.applicationId(), CAPABILITY_CODE, servletRequest.getRemoteAddr());
        return ApiResponse.ok(decisionService.listRecommendations(plan_code, status, ctx.applicationId()));
    }

    @PostMapping("/recommendations/{id}/feedback")
    @Operation(summary = "Submit outcome tracking and user rating feedback for recommendation")
    public ApiResponse<DecisionFeedbackView> submitFeedback(
            @AuthenticationPrincipal VerifiedRequestContext ctx,
            @PathVariable UUID id,
            @Valid @RequestBody RecommendationFeedbackRequest request,
            HttpServletRequest servletRequest
    ) {
        entitlementService.assertEntitled(ctx.applicationId(), CAPABILITY_CODE, servletRequest.getRemoteAddr());
        return ApiResponse.ok(decisionService.submitFeedback(id, request, ctx.applicationId(), ctx.externalUserRef()));
    }
}

