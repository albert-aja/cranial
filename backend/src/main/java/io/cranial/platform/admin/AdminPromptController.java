package io.cranial.platform.admin;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.cranial.platform.orchestration.model.PromptView;
import io.cranial.platform.orchestration.service.PromptRegistryService;
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
@RequestMapping("/api/admin/v1/prompts")
@Tag(name = "Admin Prompt Registry", description = "Manage versioned prompt templates and variables")
@SecurityRequirement(name = OpenApiConfig.SCHEME_ADMIN_JWT)
public class AdminPromptController {

    private final PromptRegistryService promptService;

    public AdminPromptController(PromptRegistryService promptService) {
        this.promptService = promptService;
    }

    @GetMapping
    @Operation(summary = "List all prompts")
    public ApiResponse<List<PromptView>> listPrompts() {
        return ApiResponse.ok(promptService.listPrompts());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get prompt by id")
    public ApiResponse<PromptView> getPrompt(@PathVariable UUID id) {
        return ApiResponse.ok(promptService.getPrompt(id));
    }

    @GetMapping("/versions/{promptCode}")
    @Operation(summary = "List all versions of a prompt by code")
    public ApiResponse<List<PromptView>> listVersions(@PathVariable String promptCode) {
        return ApiResponse.ok(promptService.listVersions(promptCode));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'PLATFORM_OPERATOR')")
    @Operation(summary = "Create or update prompt template")
    public ApiResponse<PromptView> savePrompt(@Valid @RequestBody SavePromptRequest req) {
        PromptView view = promptService.createOrUpdatePrompt(
                req.id(),
                req.promptCode(),
                req.name(),
                req.description(),
                req.capabilityCode(),
                req.taskCode(),
                req.applicationId(),
                req.version(),
                req.status(),
                req.systemTemplate(),
                req.userTemplate(),
                req.inputVariablesJson()
        );
        return ApiResponse.ok(view);
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'PLATFORM_OPERATOR')")
    @Operation(summary = "Publish prompt version and retire older versions")
    public ApiResponse<PromptView> publishPrompt(@PathVariable UUID id) {
        return ApiResponse.ok(promptService.publishVersion(id));
    }

    public record SavePromptRequest(
            UUID id,
            @NotBlank @JsonAlias({"promptCode", "prompt_code"}) String promptCode,
            @NotBlank String name,
            String description,
            @NotBlank @JsonAlias({"capabilityCode", "capability_code"}) String capabilityCode,
            @JsonAlias({"taskCode", "task_code"}) String taskCode,
            @JsonAlias({"applicationId", "application_id"}) UUID applicationId,
            Integer version,
            String status,
            @JsonAlias({"systemTemplate", "system_template"}) String systemTemplate,
            @NotBlank @JsonAlias({"userTemplate", "user_template"}) String userTemplate,
            @JsonAlias({"inputVariablesJson", "input_variables_json"}) String inputVariablesJson
    ) {
    }
}

