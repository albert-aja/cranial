package io.cranial.platform.gateway;

import io.cranial.platform.application.capability.CapabilityEntitlementService;
import io.cranial.platform.customer.model.*;
import io.cranial.platform.customer.service.CustomerIntelligenceService;
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
@RequestMapping("/api/v1/customer")
@Tag(name = "Customer Intelligence Gateway", description = "Multilingual sentiment analysis, PII redaction, ticket classification, promise guard safety checks, review summarization, and money management feature discovery")
@SecurityRequirement(name = OpenApiConfig.SCHEME_APP_API_KEY)
public class CustomerGatewayController {

    private static final String CAPABILITY_CODE = "customer_intelligence";

    private final CustomerIntelligenceService customerService;
    private final CapabilityEntitlementService entitlementService;

    public CustomerGatewayController(
            CustomerIntelligenceService customerService,
            CapabilityEntitlementService entitlementService
    ) {
        this.customerService = customerService;
        this.entitlementService = entitlementService;
    }

    @PostMapping("/sentiment/analyze")
    @Operation(summary = "Analyze customer text sentiment, urgency, aspects, and sanitize PII")
    public ApiResponse<SentimentAnalysisResult> analyzeSentiment(
            @AuthenticationPrincipal VerifiedRequestContext ctx,
            @Valid @RequestBody SentimentAnalysisRequest request,
            HttpServletRequest servletRequest
    ) {
        entitlementService.assertEntitled(ctx.applicationId(), CAPABILITY_CODE, servletRequest.getRemoteAddr());
        return ApiResponse.ok(customerService.analyzeSentiment(request, ctx.applicationId(), ctx.tenantId(), false));
    }

    @PostMapping("/tickets/triage")
    @Operation(summary = "Triage support ticket, classify intent, assess priority, apply promise safety guards, and evaluate human escalation")
    public ApiResponse<TicketTriageResult> triageTicket(
            @AuthenticationPrincipal VerifiedRequestContext ctx,
            @Valid @RequestBody TicketTriageRequest request,
            HttpServletRequest servletRequest
    ) {
        entitlementService.assertEntitled(ctx.applicationId(), CAPABILITY_CODE, servletRequest.getRemoteAddr());
        return ApiResponse.ok(customerService.triageTicket(request, ctx.applicationId(), ctx.tenantId(), false, ctx.applicationCode()));
    }

    @PostMapping("/reviews/summarize")
    @Operation(summary = "Summarize customer reviews with pros, cons, feature requests, and evidence quotes")
    public ApiResponse<ReviewSummaryResult> summarizeReviews(
            @AuthenticationPrincipal VerifiedRequestContext ctx,
            @Valid @RequestBody ReviewSummarizeRequest request,
            HttpServletRequest servletRequest
    ) {
        entitlementService.assertEntitled(ctx.applicationId(), CAPABILITY_CODE, servletRequest.getRemoteAddr());
        return ApiResponse.ok(customerService.summarizeReviews(request, ctx.applicationId(), ctx.tenantId(), false));
    }

    @PostMapping("/products/discover")
    @Operation(summary = "Discover and recommend non-banking money management features and budgeting tools based on customer goals")
    public ApiResponse<ProductDiscoveryResult> discoverProducts(
            @AuthenticationPrincipal VerifiedRequestContext ctx,
            @Valid @RequestBody ProductDiscoveryRequest request,
            HttpServletRequest servletRequest
    ) {
        entitlementService.assertEntitled(ctx.applicationId(), CAPABILITY_CODE, servletRequest.getRemoteAddr());
        return ApiResponse.ok(customerService.discoverProducts(request, ctx.applicationId(), ctx.tenantId(), false));
    }

    @GetMapping("/tickets")
    @Operation(summary = "List triaged support tickets for authorized application and tenant")
    public ApiResponse<List<CustomerTicketView>> listTickets(
            @AuthenticationPrincipal VerifiedRequestContext ctx,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "priority", required = false) String priority,
            @RequestParam(value = "category", required = false) String category,
            HttpServletRequest servletRequest
    ) {
        entitlementService.assertEntitled(ctx.applicationId(), CAPABILITY_CODE, servletRequest.getRemoteAddr());
        return ApiResponse.ok(customerService.listTickets(ctx.applicationId(), ctx.tenantId(), status, priority, category, false));
    }

    @GetMapping("/tickets/{id}")
    @Operation(summary = "Get support ticket details")
    public ApiResponse<CustomerTicketView> getTicket(
            @AuthenticationPrincipal VerifiedRequestContext ctx,
            @PathVariable UUID id,
            HttpServletRequest servletRequest
    ) {
        entitlementService.assertEntitled(ctx.applicationId(), CAPABILITY_CODE, servletRequest.getRemoteAddr());
        return ApiResponse.ok(customerService.getTicket(id, ctx.applicationId(), ctx.tenantId(), false));
    }
}

