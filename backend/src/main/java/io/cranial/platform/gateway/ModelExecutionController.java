package io.cranial.platform.gateway;

import io.cranial.platform.application.capability.CapabilityEntitlementService;
import io.cranial.platform.platform.common.ApiResponse;
import io.cranial.platform.platform.config.OpenApiConfig;
import io.cranial.platform.provider.model.ChatCompletionRequest;
import io.cranial.platform.provider.model.ChatCompletionResponse;
import io.cranial.platform.provider.model.EmbeddingRequest;
import io.cranial.platform.provider.model.EmbeddingResponse;
import io.cranial.platform.provider.service.ModelExecutionService;
import io.cranial.platform.security.VerifiedRequestContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/models")
@Tag(name = "Model Execution Gateway", description = "Execute AI models via CRANIAL local-first orchestration")
@SecurityRequirement(name = OpenApiConfig.SCHEME_APP_API_KEY)
public class ModelExecutionController {

    private final ModelExecutionService executionService;
    private final CapabilityEntitlementService entitlementService;

    public ModelExecutionController(
            ModelExecutionService executionService,
            CapabilityEntitlementService entitlementService
    ) {
        this.executionService = executionService;
        this.entitlementService = entitlementService;
    }

    @PostMapping("/chat")
    @Operation(summary = "Execute chat completion with local-first governance and strict no-paid-fallback")
    public ApiResponse<ChatCompletionResponse> chat(
            @AuthenticationPrincipal VerifiedRequestContext ctx,
            @Valid @RequestBody ChatCompletionRequest request,
            HttpServletRequest servletRequest
    ) {
        String clientIp = servletRequest.getRemoteAddr();
        entitlementService.assertEntitled(ctx.applicationId(), request.capability(), clientIp);

        ChatCompletionResponse response = executionService.executeChat(ctx, request, clientIp);
        return ApiResponse.ok(response);
    }

    @PostMapping(value = "/chat/stream", produces = org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Execute streaming chat completion with local-first governance and real-time SSE chunks")
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter chatStream(
            @AuthenticationPrincipal VerifiedRequestContext ctx,
            @Valid @RequestBody ChatCompletionRequest request,
            HttpServletRequest servletRequest
    ) {
        String clientIp = servletRequest.getRemoteAddr();
        entitlementService.assertEntitled(ctx.applicationId(), request.capability(), clientIp);

        return executionService.executeChatStream(ctx, request, clientIp);
    }

    @PostMapping("/embed")
    @Operation(summary = "Generate vector embeddings with local-first governance")
    public ApiResponse<EmbeddingResponse> embed(
            @AuthenticationPrincipal VerifiedRequestContext ctx,
            @Valid @RequestBody EmbeddingRequest request,
            HttpServletRequest servletRequest
    ) {
        String clientIp = servletRequest.getRemoteAddr();
        entitlementService.assertEntitled(ctx.applicationId(), request.capability(), clientIp);

        EmbeddingResponse response = executionService.executeEmbed(ctx, request, clientIp);
        return ApiResponse.ok(response);
    }
}

