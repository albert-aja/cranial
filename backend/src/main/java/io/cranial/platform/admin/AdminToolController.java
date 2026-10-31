package io.cranial.platform.admin;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.cranial.platform.orchestration.model.ToolExecutionRequest;
import io.cranial.platform.orchestration.model.ToolExecutionResult;
import io.cranial.platform.orchestration.model.ToolView;
import io.cranial.platform.orchestration.service.ToolRegistryService;
import io.cranial.platform.platform.common.ApiResponse;
import io.cranial.platform.platform.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/v1/tools")
@Tag(name = "Admin Tool Registry", description = "Register application tools with strict read-only/no-payments safety")
@SecurityRequirement(name = OpenApiConfig.SCHEME_ADMIN_JWT)
public class AdminToolController {

    private final ToolRegistryService toolService;

    public AdminToolController(ToolRegistryService toolService) {
        this.toolService = toolService;
    }

    @GetMapping
    @Operation(summary = "List all registered tools")
    public ApiResponse<List<ToolView>> listTools() {
        return ApiResponse.ok(toolService.listTools());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get tool definition by id")
    public ApiResponse<ToolView> getTool(@PathVariable UUID id) {
        return ApiResponse.ok(toolService.getTool(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'PLATFORM_OPERATOR')")
    @Operation(summary = "Create or update registered tool")
    public ApiResponse<ToolView> saveTool(@Valid @RequestBody SaveToolRequest req) {
        ToolView view = toolService.saveTool(
                req.id(),
                req.toolCode(),
                req.name(),
                req.description(),
                req.applicationId(),
                req.inputSchemaId(),
                req.outputSchemaId(),
                req.executionType(),
                req.endpointUrl(),
                req.isReadOnly() == null || req.isReadOnly(),
                req.requiredPermission(),
                req.riskLevel(),
                req.timeoutMs() != null ? req.timeoutMs() : 5000,
                req.status()
        );
        return ApiResponse.ok(view);
    }

    @PostMapping("/{id}/test")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'PLATFORM_OPERATOR')")
    @Operation(summary = "Test execute a tool in safe sandboxed environment")
    public ApiResponse<ToolExecutionResult> testRunTool(
            @PathVariable UUID id,
            @RequestBody ToolExecutionRequest req
    ) {
        return ApiResponse.ok(toolService.testRunTool(id, req));
    }

    public record SaveToolRequest(
            UUID id,
            @NotBlank @JsonAlias({"toolCode", "tool_code"}) String toolCode,
            @NotBlank String name,
            String description,
            @JsonAlias({"applicationId", "application_id"}) UUID applicationId,
            @JsonAlias({"inputSchemaId", "input_schema_id"}) UUID inputSchemaId,
            @JsonAlias({"outputSchemaId", "output_schema_id"}) UUID outputSchemaId,
            @JsonAlias({"executionType", "execution_type"}) String executionType,
            @JsonAlias({"endpointUrl", "endpoint_url"}) String endpointUrl,
            @JsonAlias({"isReadOnly", "is_read_only"}) Boolean isReadOnly,
            @JsonAlias({"requiredPermission", "required_permission"}) String requiredPermission,
            @JsonAlias({"riskLevel", "risk_level"}) String riskLevel,
            @JsonAlias({"timeoutMs", "timeout_ms"}) Integer timeoutMs,
            String status
    ) {
    }
}

