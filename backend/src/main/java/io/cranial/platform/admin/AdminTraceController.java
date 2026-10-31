package io.cranial.platform.admin;

import io.cranial.platform.orchestration.model.OrchestratorTraceView;
import io.cranial.platform.orchestration.service.TraceService;
import io.cranial.platform.platform.common.ApiResponse;
import io.cranial.platform.platform.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/v1/traces")
@Tag(name = "Admin Orchestrator Traces", description = "Audit and inspect step-by-step orchestrator execution traces")
@SecurityRequirement(name = OpenApiConfig.SCHEME_ADMIN_JWT)
public class AdminTraceController {

    private final TraceService traceService;

    public AdminTraceController(TraceService traceService) {
        this.traceService = traceService;
    }

    @GetMapping
    @Operation(summary = "List recent orchestrator execution traces")
    public ApiResponse<List<OrchestratorTraceView>> listRecent(
            @RequestParam(required = false) UUID applicationId,
            @RequestParam(defaultValue = "50") int limit
    ) {
        return ApiResponse.ok(traceService.listRecent(applicationId, limit));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get full trace detail including steps and tool calls")
    public ApiResponse<OrchestratorTraceView> getTrace(@PathVariable UUID id) {
        return ApiResponse.ok(traceService.getTrace(id));
    }
}

