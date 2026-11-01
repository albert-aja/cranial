package io.cranial.platform.admin;

import io.cranial.platform.customer.entity.CustomerEvalCaseEntity;
import io.cranial.platform.customer.model.*;
import io.cranial.platform.customer.service.CustomerIntelligenceService;
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
@RequestMapping("/api/admin/v1/customer")
@Tag(name = "Admin Customer Intelligence Console", description = "Administration of support ticket triage, Indonesian slang & colloquial spelling normalization, human escalation handoff queue, eval benchmarks, and customer sentiment analytics")
@SecurityRequirement(name = OpenApiConfig.SCHEME_ADMIN_JWT)
public class AdminCustomerController {

    private final CustomerIntelligenceService customerService;

    public AdminCustomerController(CustomerIntelligenceService customerService) {
        this.customerService = customerService;
    }

    @PostMapping("/slang/normalize")
    @PreAuthorize("hasAuthority('app:read')")
    @Operation(summary = "Normalize Indonesian slang and colloquial spellings in customer support chat")
    public ApiResponse<CustomerSlangNormalizeResult> normalizeSupportChat(
            @Valid @RequestBody CustomerSlangNormalizeRequest request
    ) {
        return ApiResponse.ok(customerService.normalizeSupportChat(request));
    }

    @GetMapping("/slang/lexicon")
    @PreAuthorize("hasAuthority('app:read')")
    @Operation(summary = "List built-in and custom Indonesian customer support slang lexicon rules")
    public ApiResponse<List<CustomerSlangLexiconView>> listSlangLexicon(
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "search", required = false) String search
    ) {
        return ApiResponse.ok(customerService.listSlangLexicon(category, search));
    }

    @PostMapping("/slang/lexicon")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('app:write')")
    @Operation(summary = "Create or update a governed custom Indonesian slang normalization rule")
    public ApiResponse<CustomerSlangLexiconView> saveSlangLexiconEntry(
            @AuthenticationPrincipal AdminPrincipal admin,
            @Valid @RequestBody CustomerSlangLexiconRequest request
    ) {
        String actor = admin != null ? admin.email() : "operator";
        return ApiResponse.ok(customerService.saveSlangLexiconEntry(request, actor));
    }

    @DeleteMapping("/slang/lexicon/{id}")
    @PreAuthorize("hasAuthority('app:write')")
    @Operation(summary = "Delete a custom Indonesian slang normalization rule")
    public ApiResponse<Map<String, Object>> deleteSlangLexiconEntry(
            @PathVariable UUID id,
            @AuthenticationPrincipal AdminPrincipal admin
    ) {
        String actor = admin != null ? admin.email() : "operator";
        customerService.deleteSlangLexiconEntry(id, actor);
        return ApiResponse.ok(Map.of("id", id.toString(), "deleted", true));
    }

    @PostMapping("/sentiment/analyze")
    @PreAuthorize("hasAuthority('app:read')")
    @Operation(summary = "Operator console simulator for sentiment analysis, PII masking, and slang normalization")
    public ApiResponse<SentimentAnalysisResult> analyzeSentiment(@Valid @RequestBody SentimentAnalysisRequest request) {
        return ApiResponse.ok(customerService.analyzeSentiment(request, null, null, true));
    }

    @PostMapping("/tickets/triage")
    @PreAuthorize("hasAuthority('app:read')")
    @Operation(summary = "Operator console simulator for support ticket triage, slang normalization, and promise safety")
    public ApiResponse<TicketTriageResult> triageTicket(@Valid @RequestBody TicketTriageRequest request) {
        return ApiResponse.ok(customerService.triageTicket(request, null, null, true, "admin_console"));
    }

    @PostMapping("/reviews/summarize")
    @PreAuthorize("hasAuthority('app:read')")
    @Operation(summary = "Operator console simulator for customer review summarization")
    public ApiResponse<ReviewSummaryResult> summarizeReviews(@Valid @RequestBody ReviewSummarizeRequest request) {
        return ApiResponse.ok(customerService.summarizeReviews(request, null, null, true));
    }

    @PostMapping("/products/discover")
    @PreAuthorize("hasAuthority('app:read')")
    @Operation(summary = "Operator console simulator for non-banking money management feature discovery")
    public ApiResponse<ProductDiscoveryResult> discoverProducts(@Valid @RequestBody ProductDiscoveryRequest request) {
        return ApiResponse.ok(customerService.discoverProducts(request, null, null, true));
    }

    @GetMapping("/tickets")
    @PreAuthorize("hasAuthority('app:read')")
    @Operation(summary = "List all customer support tickets across applications")
    public ApiResponse<List<CustomerTicketView>> listTickets(
            @RequestParam(value = "application_id", required = false) UUID applicationId,
            @RequestParam(value = "tenant_id", required = false) UUID tenantId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "priority", required = false) String priority,
            @RequestParam(value = "category", required = false) String category
    ) {
        return ApiResponse.ok(customerService.listTickets(applicationId, tenantId, status, priority, category, true));
    }

    @GetMapping("/tickets/{id}")
    @PreAuthorize("hasAuthority('app:read')")
    @Operation(summary = "Get specific customer support ticket")
    public ApiResponse<CustomerTicketView> getTicket(@PathVariable UUID id) {
        return ApiResponse.ok(customerService.getTicket(id, null, null, true));
    }

    @GetMapping("/escalations")
    @PreAuthorize("hasAuthority('app:read')")
    @Operation(summary = "List human escalation handoff queue")
    public ApiResponse<List<CustomerEscalationView>> listEscalations(
            @RequestParam(value = "application_id", required = false) UUID applicationId,
            @RequestParam(value = "tenant_id", required = false) UUID tenantId,
            @RequestParam(value = "status", required = false) String status
    ) {
        return ApiResponse.ok(customerService.listEscalations(applicationId, tenantId, status, true));
    }

    @PostMapping("/escalations/{id}/resolve")
    @PreAuthorize("hasAuthority('app:write')")
    @Operation(summary = "Mark human escalation and linked ticket as resolved")
    public ApiResponse<CustomerEscalationView> resolveEscalation(
            @PathVariable UUID id,
            @AuthenticationPrincipal AdminPrincipal admin,
            @RequestBody(required = false) Map<String, String> body
    ) {
        String agentName = admin != null ? admin.email() : "operator";
        String notes = body != null && body.containsKey("resolution_notes") ? body.get("resolution_notes") : "Resolved by operator";
        return ApiResponse.ok(customerService.resolveEscalation(id, agentName, notes, null, null, true));
    }

    @PostMapping("/benchmarks/run")
    @PreAuthorize("hasAuthority('app:read')")
    @Operation(summary = "Execute Customer Intelligence regression benchmark suite (sentiment, classification, promise safety, escalation, slang normalization)")
    public ApiResponse<CustomerBenchmarkResult> runBenchmarks() {
        return ApiResponse.ok(customerService.runEvaluationBenchmarks());
    }

    @PostMapping("/eval-cases")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('app:write')")
    @Operation(summary = "Register new Customer Intelligence benchmark evaluation case")
    public ApiResponse<CustomerEvalCaseEntity> createEvalCase(@Valid @RequestBody CustomerEvalCaseRequest request) {
        return ApiResponse.ok(customerService.createEvalCase(request));
    }
}
