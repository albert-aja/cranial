package io.cranial.platform.admin;

import io.cranial.platform.application.registry.ApplicationEntity;
import io.cranial.platform.application.registry.ApplicationMapper;
import io.cranial.platform.application.registry.ApplicationSettingsEntity;
import io.cranial.platform.application.registry.ApplicationSettingsMapper;
import io.cranial.platform.platform.common.ApiResponse;
import io.cranial.platform.platform.config.OpenApiConfig;
import io.cranial.platform.provider.model.ChatCompletionRequest;
import io.cranial.platform.provider.model.ChatCompletionResponse;
import io.cranial.platform.provider.model.ChatMessage;
import io.cranial.platform.provider.model.ModelView;
import io.cranial.platform.provider.model.ModelWarmupResult;
import io.cranial.platform.provider.service.ModelExecutionService;
import io.cranial.platform.provider.service.ProviderService;
import io.cranial.platform.provider.telemetry.SystemHardwareMetricsService;
import io.cranial.platform.security.VerifiedRequestContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/v1/models")
@Tag(name = "Admin Model Registry", description = "Register and manage AI models, context windows, and capability mappings")
@SecurityRequirement(name = OpenApiConfig.SCHEME_ADMIN_JWT)
public class AdminModelController {

    private final ProviderService providerService;
    private final ModelExecutionService modelExecutionService;
    private final ApplicationMapper applicationMapper;
    private final ApplicationSettingsMapper settingsMapper;
    private final SystemHardwareMetricsService hardwareMetricsService;

    public AdminModelController(
            ProviderService providerService,
            ModelExecutionService modelExecutionService,
            ApplicationMapper applicationMapper,
            ApplicationSettingsMapper settingsMapper,
            SystemHardwareMetricsService hardwareMetricsService
    ) {
        this.providerService = providerService;
        this.modelExecutionService = modelExecutionService;
        this.applicationMapper = applicationMapper;
        this.settingsMapper = settingsMapper;
        this.hardwareMetricsService = hardwareMetricsService;
    }

    @GetMapping
    @Operation(summary = "List all models in registry with capability tags")
    public ApiResponse<List<ModelView>> listModels() {
        return ApiResponse.ok(providerService.listModels());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'PLATFORM_OPERATOR')")
    @Operation(summary = "Create or update model in registry")
    public ApiResponse<ModelView> saveModel(@Valid @RequestBody SaveModelRequest req) {
        ModelView view = providerService.saveModel(
                req.id(),
                req.providerId(),
                req.modelIdentifier(),
                req.displayName(),
                req.modality(),
                req.contextWindowTokens() != null ? req.contextWindowTokens() : 8192,
                req.maxOutputTokens() != null ? req.maxOutputTokens() : 2048,
                req.costPerMillionInputCents() != null ? req.costPerMillionInputCents() : 0,
                req.costPerMillionOutputCents() != null ? req.costPerMillionOutputCents() : 0,
                req.isLocal() == null || req.isLocal(),
                req.status(),
                req.capabilities(),
                req.metadataJson()
        );
        return ApiResponse.ok(view);
    }

    @PostMapping("/{id}/warmup")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'PLATFORM_OPERATOR')")
    @Operation(summary = "Send single-token probe to warm up model in runtime memory/VRAM")
    public ApiResponse<ModelWarmupResult> warmupModel(@PathVariable UUID id) {
        return ApiResponse.ok(providerService.warmupModel(id));
    }

    @GetMapping("/hardware-metrics")
    @PreAuthorize("hasAuthority('app:read')")
    @Operation(summary = "Real-time local GPU, CPU, and RAM utilization telemetry")
    public ApiResponse<SystemHardwareMetricsService.HardwareUtilizationSnapshot> getHardwareMetrics() {
        return ApiResponse.ok(hardwareMetricsService.getSnapshot());
    }

    @PostMapping("/playground")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'PLATFORM_OPERATOR')")
    @Operation(summary = "Admin interactive playground test chat execution")
    public ApiResponse<ChatCompletionResponse> playgroundChat(
            @Valid @RequestBody PlaygroundChatRequest req,
            HttpServletRequest servletRequest
    ) {
        VerifiedRequestContext ctx = resolveContext(req.applicationId());
        ChatCompletionRequest chatRequest = toChatRequest(req);
        ChatCompletionResponse response = modelExecutionService.executeChat(ctx, chatRequest, servletRequest.getRemoteAddr());
        return ApiResponse.ok(response);
    }

    @PostMapping(value = "/playground/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'PLATFORM_OPERATOR')")
    @Operation(summary = "Admin interactive playground streaming test chat")
    public SseEmitter playgroundStream(
            @Valid @RequestBody PlaygroundChatRequest req,
            HttpServletRequest servletRequest
    ) {
        VerifiedRequestContext ctx = resolveContext(req.applicationId());
        ChatCompletionRequest chatRequest = toChatRequest(req);
        return modelExecutionService.executeChatStream(ctx, chatRequest, servletRequest.getRemoteAddr());
    }

    private VerifiedRequestContext resolveContext(UUID applicationId) {
        ApplicationEntity app = null;
        if (applicationId != null) {
            app = applicationMapper.selectById(applicationId);
        }
        if (app == null) {
            List<ApplicationEntity> all = applicationMapper.selectList(null);
            if (!all.isEmpty()) {
                app = all.get(0);
            }
        }

        boolean allowHosted = false;
        boolean allowSensitive = false;
        if (app != null) {
            ApplicationSettingsEntity settings = settingsMapper.selectById(app.getId());
            if (settings != null) {
                allowHosted = Boolean.TRUE.equals(settings.getAllowHostedProviders());
                allowSensitive = Boolean.TRUE.equals(settings.getAllowSensitiveExternal());
            }
        }

        return new VerifiedRequestContext(
                app != null ? app.getId() : UUID.randomUUID(),
                app != null ? app.getCode() : "admin-playground",
                app != null ? app.getEnvironment() : "DEV",
                null,
                "admin-pg",
                false,
                null,
                null,
                "admin-operator",
                List.of("ROLE_SUPER_ADMIN"),
                List.of("capability:all"),
                "playground-" + UUID.randomUUID(),
                allowHosted,
                allowSensitive
        );
    }

    private ChatCompletionRequest toChatRequest(PlaygroundChatRequest req) {
        String capability = (req.capability() != null && !req.capability().isBlank())
                ? req.capability()
                : "conversational_intelligence";
        String sensitivity = (req.dataSensitivity() != null && !req.dataSensitivity().isBlank())
                ? req.dataSensitivity()
                : "RESTRICTED";
        double temp = req.temperature() != null ? req.temperature() : 0.7;
        int maxTokens = req.maxTokens() != null ? req.maxTokens() : 1024;

        return new ChatCompletionRequest(
                capability,
                req.task(),
                req.messages(),
                temp,
                maxTokens,
                req.modelOverride(),
                sensitivity,
                Map.of("source", "admin_playground")
        );
    }

    public record PlaygroundChatRequest(
            @com.fasterxml.jackson.annotation.JsonAlias({"applicationId", "application_id"}) UUID applicationId,
            @com.fasterxml.jackson.annotation.JsonAlias({"modelOverride", "model_override"}) String modelOverride,
            String capability,
            String task,
            @com.fasterxml.jackson.annotation.JsonAlias({"dataSensitivity", "data_sensitivity"}) String dataSensitivity,
            Double temperature,
            @com.fasterxml.jackson.annotation.JsonAlias({"maxTokens", "max_tokens"}) Integer maxTokens,
            @NotEmpty List<ChatMessage> messages
    ) {}

    public record SaveModelRequest(
            UUID id,
            @NotNull @com.fasterxml.jackson.annotation.JsonAlias({"providerId", "provider_id"}) UUID providerId,
            @NotBlank @com.fasterxml.jackson.annotation.JsonAlias({"modelIdentifier", "model_identifier"}) String modelIdentifier,
            @NotBlank @com.fasterxml.jackson.annotation.JsonAlias({"displayName", "display_name"}) String displayName,
            @NotBlank String modality,
            @com.fasterxml.jackson.annotation.JsonAlias({"contextWindowTokens", "context_window_tokens"}) Integer contextWindowTokens,
            @com.fasterxml.jackson.annotation.JsonAlias({"maxOutputTokens", "max_output_tokens"}) Integer maxOutputTokens,
            @com.fasterxml.jackson.annotation.JsonAlias({"costPerMillionInputCents", "cost_per_million_input_cents"}) Integer costPerMillionInputCents,
            @com.fasterxml.jackson.annotation.JsonAlias({"costPerMillionOutputCents", "cost_per_million_output_cents"}) Integer costPerMillionOutputCents,
            @com.fasterxml.jackson.annotation.JsonAlias({"isLocal", "is_local"}) Boolean isLocal,
            String status,
            List<String> capabilities,
            @com.fasterxml.jackson.annotation.JsonAlias({"metadataJson", "metadata_json"}) String metadataJson
    ) {
    }
}
