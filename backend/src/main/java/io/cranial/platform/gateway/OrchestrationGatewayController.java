package io.cranial.platform.gateway;

import io.cranial.platform.application.capability.CapabilityEntitlementService;
import io.cranial.platform.orchestration.engine.AiOrchestrator;
import io.cranial.platform.orchestration.model.OrchestratorRequest;
import io.cranial.platform.orchestration.model.OrchestratorResult;
import io.cranial.platform.platform.common.ApiResponse;
import io.cranial.platform.platform.config.OpenApiConfig;
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
@RequestMapping("/api/v1/orchestrator")
@Tag(name = "Orchestrator Gateway", description = "Coordinate tasks, versioned prompts, tools, and structured outputs")
@SecurityRequirement(name = OpenApiConfig.SCHEME_APP_API_KEY)
public class OrchestrationGatewayController {

    private final AiOrchestrator aiOrchestrator;
    private final CapabilityEntitlementService entitlementService;

    public OrchestrationGatewayController(
            AiOrchestrator aiOrchestrator,
            CapabilityEntitlementService entitlementService
    ) {
        this.aiOrchestrator = aiOrchestrator;
        this.entitlementService = entitlementService;
    }

    @PostMapping("/execute")
    @Operation(summary = "Execute orchestrated task with prompt interpolation, tool execution, and schema validation")
    public ApiResponse<OrchestratorResult> execute(
            @AuthenticationPrincipal VerifiedRequestContext ctx,
            @Valid @RequestBody OrchestratorRequest request,
            HttpServletRequest servletRequest
    ) {
        String clientIp = servletRequest.getRemoteAddr();
        entitlementService.assertEntitled(ctx.applicationId(), request.capabilityCode(), clientIp);

        OrchestratorResult result = aiOrchestrator.execute(ctx, request, clientIp);
        return ApiResponse.ok(result);
    }
}

