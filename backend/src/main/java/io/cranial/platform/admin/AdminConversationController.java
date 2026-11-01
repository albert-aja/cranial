package io.cranial.platform.admin;

import io.cranial.platform.conversation.model.*;
import io.cranial.platform.conversation.service.ConversationService;
import io.cranial.platform.platform.common.ApiResponse;
import io.cranial.platform.platform.config.OpenApiConfig;
import io.cranial.platform.security.AdminPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/v1/conversations")
@Tag(name = "Admin Conversational Intelligence Console", description = "Administration of multi-turn conversational assistant sessions, Indonesian slang normalization, non-banking safety guard monitoring, and retention policy management")
@SecurityRequirement(name = OpenApiConfig.SCHEME_ADMIN_JWT)
public class AdminConversationController {

    private final ConversationService conversationService;

    public AdminConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @GetMapping
    @Operation(summary = "List conversation sessions with application, tenant, query, and status filters")
    public ApiResponse<List<ConversationView>> listConversations(
            @RequestParam(required = false) UUID applicationId,
            @RequestParam(required = false) UUID tenantId,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return ApiResponse.ok(conversationService.listConversations(applicationId, tenantId, query, status, page, size));
    }

    @GetMapping("/{conversationId}")
    @Operation(summary = "Get full conversation session details, message turn history, and operator feedback")
    public ApiResponse<ConversationDetailView> getConversation(@PathVariable UUID conversationId) {
        return ApiResponse.ok(conversationService.getConversation(conversationId));
    }

    @PostMapping("/simulate")
    @Operation(summary = "Simulate an interactive conversation turn for operator testing")
    public ApiResponse<ConversationResponse> simulateTurn(
            @RequestParam UUID applicationId,
            @RequestParam UUID conversationId,
            @Valid @RequestBody ConversationMessageRequest request
    ) {
        return ApiResponse.ok(conversationService.sendMessage(applicationId, conversationId, request));
    }

    @PostMapping("/create-session")
    @Operation(summary = "Create an operator testing conversation session")
    public ApiResponse<ConversationDetailView> createSession(
            @RequestParam UUID applicationId,
            @RequestParam(required = false) UUID tenantId,
            @Valid @RequestBody ConversationCreateRequest request
    ) {
        return ApiResponse.ok(conversationService.createConversation(applicationId, tenantId, request));
    }

    @DeleteMapping("/{conversationId}")
    @Operation(summary = "Delete conversation session (soft-delete or hard-delete)")
    public ApiResponse<Void> deleteConversation(
            @PathVariable UUID conversationId,
            @RequestParam(defaultValue = "false") boolean hardDelete
    ) {
        conversationService.deleteConversation(conversationId, hardDelete);
        return ApiResponse.ok(null);
    }

    @GetMapping("/{conversationId}/export")
    @Operation(summary = "Export conversation session in Markdown or JSON")
    public ApiResponse<ConversationExportResponse> exportConversation(
            @PathVariable UUID conversationId,
            @RequestParam(defaultValue = "markdown") String format
    ) {
        return ApiResponse.ok(conversationService.exportConversation(conversationId, format));
    }

    @PostMapping("/{conversationId}/feedback")
    @Operation(summary = "Submit operator evaluation feedback on conversation accuracy and helpfulness")
    public ApiResponse<ConversationFeedbackView> addFeedback(
            @AuthenticationPrincipal AdminPrincipal admin,
            @PathVariable UUID conversationId,
            @Valid @RequestBody ConversationFeedbackRequest request
    ) {
        UUID opId = admin != null ? admin.userId() : null;
        return ApiResponse.ok(conversationService.addFeedback(conversationId, request, opId));
    }

    @PostMapping("/normalize-slang")
    @Operation(summary = "Test Indonesian slang and colloquialism normalization in Admin Console")
    public ApiResponse<SlangNormalizeResult> testSlangNormalization(@Valid @RequestBody SlangNormalizeRequest request) {
        return ApiResponse.ok(conversationService.normalizeSlang(request));
    }

    @GetMapping("/retention")
    @Operation(summary = "List conversation retention policies across registered applications")
    public ApiResponse<List<ConversationRetentionPolicyView>> getRetentionPolicies() {
        return ApiResponse.ok(conversationService.getRetentionPolicies());
    }

    @GetMapping("/retention/{applicationId}")
    @Operation(summary = "Get conversation retention policy for a specific application")
    public ApiResponse<ConversationRetentionPolicyView> getRetentionPolicy(@PathVariable UUID applicationId) {
        return ApiResponse.ok(conversationService.getRetentionPolicy(applicationId));
    }

    @PutMapping("/retention/{applicationId}")
    @Operation(summary = "Update conversation retention policy for an application")
    public ApiResponse<ConversationRetentionPolicyView> updateRetentionPolicy(
            @PathVariable UUID applicationId,
            @Valid @RequestBody RetentionPolicyUpdateRequest request
    ) {
        return ApiResponse.ok(conversationService.updateRetentionPolicy(applicationId, request));
    }

    @PostMapping("/prune")
    @Operation(summary = "Manually trigger background conversation pruning across expired retention windows")
    public ApiResponse<Map<String, Object>> pruneConversations() {
        int pruned = conversationService.pruneExpiredConversations();
        return ApiResponse.ok(Map.of("prunedCount", pruned, "status", "SUCCESS"));
    }

    @GetMapping("/benchmarks")
    @Operation(summary = "Retrieve conversational intelligence quality, safety, and operational benchmark metrics")
    public ApiResponse<ConversationBenchmarkResult> getBenchmarks() {
        return ApiResponse.ok(conversationService.getBenchmarks());
    }
}

