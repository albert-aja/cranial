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
@Tag(name = "Admin Customer Intelligence Console", description = "Administration of support ticket triage, human escalation handoff queue, eval benchmarks, and customer sentiment analytics")
@SecurityRequirement(name = OpenApiConfig.SCHEME_ADMIN_JWT)
public class AdminCustomerController {

    private final CustomerIntelligenceService customerService;

    public AdminCustomerController(CustomerIntelligenceService customerService) {
        this.customerService = customerService;
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
    @Operation(summary = "Execute Customer Intelligence regression benchmark suite (sentiment, classification, promise safety, escalation)")
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

