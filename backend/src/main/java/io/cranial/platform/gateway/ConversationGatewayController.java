package io.cranial.platform.gateway;

import io.cranial.platform.application.capability.CapabilityEntitlementService;
import io.cranial.platform.conversation.model.*;
import io.cranial.platform.conversation.service.ConversationService;
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
@RequestMapping("/api/v1/conversations")
@Tag(name = "Conversational Intelligence Gateway", description = "Multi-turn conversational assistant, Indonesian slang & financial term normalization, context window sliding memory, structured UI blocks, clarification workflows, and strict non-banking money management safety enforcement")
@SecurityRequirement(name = OpenApiConfig.SCHEME_APP_API_KEY)
public class ConversationGatewayController {

    private static final String CAPABILITY_CODE = "conversational_intelligence";

    private final ConversationService conversationService;
    private final CapabilityEntitlementService entitlementService;

    public ConversationGatewayController(
            ConversationService conversationService,
            CapabilityEntitlementService entitlementService
    ) {
        this.conversationService = conversationService;
        this.entitlementService = entitlementService;
    }

    @PostMapping
    @Operation(summary = "Create a new multi-turn conversation session with isolated application context")
    public ApiResponse<ConversationDetailView> createConversation(
            @AuthenticationPrincipal VerifiedRequestContext ctx,
            @Valid @RequestBody ConversationCreateRequest request,
            HttpServletRequest servletRequest
    ) {
        entitlementService.assertEntitled(ctx.applicationId(), CAPABILITY_CODE, servletRequest.getRemoteAddr());
        return ApiResponse.ok(conversationService.createConversation(ctx.applicationId(), ctx.tenantId(), request));
    }

    @PostMapping("/{conversationId}/messages")
    @Operation(summary = "Send a message in a conversation session, receiving structured UI blocks and non-banking safe responses")
    public ApiResponse<ConversationResponse> sendMessage(
            @AuthenticationPrincipal VerifiedRequestContext ctx,
            @PathVariable UUID conversationId,
            @Valid @RequestBody ConversationMessageRequest request,
            HttpServletRequest servletRequest
    ) {
        entitlementService.assertEntitled(ctx.applicationId(), CAPABILITY_CODE, servletRequest.getRemoteAddr());
        return ApiResponse.ok(conversationService.sendMessage(ctx.applicationId(), conversationId, request));
    }

    @GetMapping("/{conversationId}")
    @Operation(summary = "Get conversation session details, messages, and feedback")
    public ApiResponse<ConversationDetailView> getConversation(
            @AuthenticationPrincipal VerifiedRequestContext ctx,
            @PathVariable UUID conversationId,
            HttpServletRequest servletRequest
    ) {
        entitlementService.assertEntitled(ctx.applicationId(), CAPABILITY_CODE, servletRequest.getRemoteAddr());
        return ApiResponse.ok(conversationService.getConversation(conversationId));
    }

    @GetMapping
    @Operation(summary = "List conversation sessions filtered by status or search query")
    public ApiResponse<List<ConversationView>> listConversations(
            @AuthenticationPrincipal VerifiedRequestContext ctx,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            HttpServletRequest servletRequest
    ) {
        entitlementService.assertEntitled(ctx.applicationId(), CAPABILITY_CODE, servletRequest.getRemoteAddr());
        return ApiResponse.ok(conversationService.listConversations(ctx.applicationId(), ctx.tenantId(), query, status, page, size));
    }

    @PostMapping("/{conversationId}/feedback")
    @Operation(summary = "Submit user feedback on conversation helpfulness and correctness")
    public ApiResponse<ConversationFeedbackView> addFeedback(
            @AuthenticationPrincipal VerifiedRequestContext ctx,
            @PathVariable UUID conversationId,
            @Valid @RequestBody ConversationFeedbackRequest request,
            HttpServletRequest servletRequest
    ) {
        entitlementService.assertEntitled(ctx.applicationId(), CAPABILITY_CODE, servletRequest.getRemoteAddr());
        return ApiResponse.ok(conversationService.addFeedback(conversationId, request, null));
    }

    @GetMapping("/{conversationId}/export")
    @Operation(summary = "Export conversation session in Markdown or JSON format")
    public ApiResponse<ConversationExportResponse> exportConversation(
            @AuthenticationPrincipal VerifiedRequestContext ctx,
            @PathVariable UUID conversationId,
            @RequestParam(defaultValue = "markdown") String format,
            HttpServletRequest servletRequest
    ) {
        entitlementService.assertEntitled(ctx.applicationId(), CAPABILITY_CODE, servletRequest.getRemoteAddr());
        return ApiResponse.ok(conversationService.exportConversation(conversationId, format));
    }

    @DeleteMapping("/{conversationId}")
    @Operation(summary = "Delete or purge a conversation session")
    public ApiResponse<Void> deleteConversation(
            @AuthenticationPrincipal VerifiedRequestContext ctx,
            @PathVariable UUID conversationId,
            @RequestParam(defaultValue = "false") boolean hardDelete,
            HttpServletRequest servletRequest
    ) {
        entitlementService.assertEntitled(ctx.applicationId(), CAPABILITY_CODE, servletRequest.getRemoteAddr());
        conversationService.deleteConversation(conversationId, hardDelete);
        return ApiResponse.ok(null);
    }

    @PostMapping("/normalize-slang")
    @Operation(summary = "Test Indonesian slang and colloquialism normalization for currency and expressions")
    public ApiResponse<SlangNormalizeResult> normalizeSlang(
            @AuthenticationPrincipal VerifiedRequestContext ctx,
            @Valid @RequestBody SlangNormalizeRequest request,
            HttpServletRequest servletRequest
    ) {
        entitlementService.assertEntitled(ctx.applicationId(), CAPABILITY_CODE, servletRequest.getRemoteAddr());
        return ApiResponse.ok(conversationService.normalizeSlang(request));
    }
}

