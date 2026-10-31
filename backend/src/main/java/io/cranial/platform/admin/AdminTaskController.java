package io.cranial.platform.admin;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.cranial.platform.orchestration.model.TaskDefinitionView;
import io.cranial.platform.orchestration.service.TaskRegistryService;
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
@RequestMapping("/api/admin/v1/tasks")
@Tag(name = "Admin Task Registry", description = "Manage task definitions, capability mappings, and schema bindings")
@SecurityRequirement(name = OpenApiConfig.SCHEME_ADMIN_JWT)
public class AdminTaskController {

    private final TaskRegistryService taskService;

    public AdminTaskController(TaskRegistryService taskService) {
        this.taskService = taskService;
    }

    @GetMapping
    @Operation(summary = "List all task definitions")
    public ApiResponse<List<TaskDefinitionView>> listTasks() {
        return ApiResponse.ok(taskService.listTasks());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get task definition by id")
    public ApiResponse<TaskDefinitionView> getTask(@PathVariable UUID id) {
        return ApiResponse.ok(taskService.getTask(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'PLATFORM_OPERATOR')")
    @Operation(summary = "Create or update task definition")
    public ApiResponse<TaskDefinitionView> saveTask(@Valid @RequestBody SaveTaskRequest req) {
        TaskDefinitionView view = taskService.saveTask(
                req.id(),
                req.taskCode(),
                req.name(),
                req.description(),
                req.capabilityCode(),
                req.defaultPromptId(),
                req.outputSchemaId(),
                req.timeoutSeconds() != null ? req.timeoutSeconds() : 60,
                req.allowedDataSensitivity() != null ? req.allowedDataSensitivity() : "RESTRICTED",
                req.isActive() == null || req.isActive()
        );
        return ApiResponse.ok(view);
    }

    public record SaveTaskRequest(
            UUID id,
            @NotBlank @JsonAlias({"taskCode", "task_code"}) String taskCode,
            @NotBlank String name,
            String description,
            @NotBlank @JsonAlias({"capabilityCode", "capability_code"}) String capabilityCode,
            @JsonAlias({"defaultPromptId", "default_prompt_id"}) UUID defaultPromptId,
            @JsonAlias({"outputSchemaId", "output_schema_id"}) UUID outputSchemaId,
            @JsonAlias({"timeoutSeconds", "timeout_seconds"}) Integer timeoutSeconds,
            @JsonAlias({"allowedDataSensitivity", "allowed_data_sensitivity"}) String allowedDataSensitivity,
            @JsonAlias({"isActive", "is_active"}) Boolean isActive
    ) {
    }
}

