package io.cranial.platform.gateway;

import io.cranial.platform.application.capability.CapabilityEntitlementService;
import io.cranial.platform.application.capability.CapabilityEntitlementView;
import io.cranial.platform.application.credential.ApplicationSandboxBootstrapService;
import io.cranial.platform.knowledge.model.KnowledgeCollectionView;
import io.cranial.platform.knowledge.service.KnowledgeIntelligenceService;
import io.cranial.platform.orchestration.entity.TaskDefinitionEntity;
import io.cranial.platform.orchestration.entity.ToolEntity;
import io.cranial.platform.orchestration.mapper.TaskDefinitionMapper;
import io.cranial.platform.orchestration.mapper.ToolMapper;
import io.cranial.platform.platform.common.ApiResponse;
import io.cranial.platform.platform.config.CranialProperties;
import io.cranial.platform.platform.config.OpenApiConfig;
import io.cranial.platform.platform.crypto.CryptoService;
import io.cranial.platform.security.SignedContextVerifier;
import io.cranial.platform.security.VerifiedRequestContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/v1/integration")
@Tag(name = "Application Integration & Readiness Gateway", description = "Endpoints for client application health checks, HMAC signed-context echo verification, authorized capability catalogs, and sandbox credentials")
@SecurityRequirement(name = OpenApiConfig.SCHEME_APP_API_KEY)
public class IntegrationHealthController {

    private final CranialProperties properties;
    private final CapabilityEntitlementService entitlementService;
    private final ApplicationSandboxBootstrapService sandboxService;
    private final TaskDefinitionMapper taskMapper;
    private final ToolMapper toolMapper;
    private final KnowledgeIntelligenceService knowledgeService;
    private final CryptoService cryptoService;

    public IntegrationHealthController(
            CranialProperties properties,
            CapabilityEntitlementService entitlementService,
            ApplicationSandboxBootstrapService sandboxService,
            TaskDefinitionMapper taskMapper,
            ToolMapper toolMapper,
            KnowledgeIntelligenceService knowledgeService,
            CryptoService cryptoService
    ) {
        this.properties = properties;
        this.entitlementService = entitlementService;
        this.sandboxService = sandboxService;
        this.taskMapper = taskMapper;
        this.toolMapper = toolMapper;
        this.knowledgeService = knowledgeService;
        this.cryptoService = cryptoService;
    }

    @GetMapping("/health")
    @Operation(summary = "Integration Health & Clock Synchronization Probe")
    public ApiResponse<Map<String, Object>> checkIntegrationHealth(
            @AuthenticationPrincipal VerifiedRequestContext ctx
    ) {
        List<String> enabledCapabilities = entitlementService.listApplicationEntitlements(ctx.applicationId()).stream()
                .filter(CapabilityEntitlementView::enabled)
                .map(CapabilityEntitlementView::capabilityCode)
                .toList();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "UP");
        data.put("platform", "CRANIAL — Universal AI Integration Platform");
        data.put("version", "v1");
        data.put("environment", properties.environment());
        data.put("server_time_utc", Instant.now().toString());
        data.put("server_epoch_seconds", Instant.now().getEpochSecond());
        data.put("max_allowed_skew_seconds", properties.security().contextMaxSkewSeconds());
        data.put("application_id", ctx.applicationId());
        data.put("application_code", ctx.applicationCode());
        data.put("key_prefix", ctx.keyPrefix());
        data.put("context_signed", ctx.contextSigned());
        data.put("entitled_capabilities", enabledCapabilities);

        return ApiResponse.ok(data);
    }

    @PostMapping("/echo")
    @Operation(summary = "Echo & Verify Signed Context and HMAC Signatures")
    public ApiResponse<Map<String, Object>> echoSignedContext(
            @AuthenticationPrincipal VerifiedRequestContext ctx,
            @RequestBody(required = false) byte[] body,
            HttpServletRequest request
    ) {
        String timestampHeader = request.getHeader(SignedContextVerifier.HEADER_TIMESTAMP);
        Long clientEpoch = null;
        Long skewSeconds = null;
        if (timestampHeader != null && !timestampHeader.isBlank()) {
            try {
                clientEpoch = Long.parseLong(timestampHeader.trim());
                skewSeconds = Math.abs(Instant.now().getEpochSecond() - clientEpoch);
            } catch (Exception ignored) {}
        }

        String bodySha256 = cryptoService.sha256Hex(body != null ? body : new byte[0]);

        Map<String, Object> echo = new LinkedHashMap<>();
        echo.put("verified", true);
        echo.put("application_id", ctx.applicationId());
        echo.put("application_code", ctx.applicationCode());
        echo.put("environment", ctx.environment());
        echo.put("context_signed", ctx.contextSigned());
        echo.put("tenant_id", ctx.tenantId());
        echo.put("external_tenant_ref", ctx.externalTenantRef());
        echo.put("external_user_ref", ctx.externalUserRef());
        echo.put("roles", ctx.roles() != null ? ctx.roles() : List.of());
        echo.put("scopes", ctx.scopes() != null ? ctx.scopes() : List.of());
        echo.put("correlation_id", ctx.correlationId());
        echo.put("request_path", request.getRequestURI());
        echo.put("http_method", request.getMethod());
        echo.put("client_timestamp_epoch", clientEpoch);
        echo.put("timestamp_skew_seconds", skewSeconds);
        echo.put("body_sha256", bodySha256);
        echo.put("timestamp_utc", Instant.now().toString());

        return ApiResponse.ok(echo);
    }

    @GetMapping("/catalog")
    @Operation(summary = "List Application-Authorized Tasks, Tools, and Knowledge Collections")
    public ApiResponse<Map<String, Object>> getApplicationCatalog(
            @AuthenticationPrincipal VerifiedRequestContext ctx
    ) {
        List<TaskDefinitionEntity> tasks = taskMapper.selectList(null).stream()
                .filter(t -> Boolean.TRUE.equals(t.getIsActive()))
                .toList();

        List<ToolEntity> tools = toolMapper.findAvailableForApplication(ctx.applicationId());

        List<KnowledgeCollectionView> collections = knowledgeService.listCollections(
                ctx.applicationId(), ctx.tenantId(), false
        );

        Map<String, Object> catalog = new LinkedHashMap<>();
        catalog.put("application_code", ctx.applicationCode());
        catalog.put("available_tasks", tasks.stream().map(t -> Map.of(
                "task_code", t.getTaskCode(),
                "name", t.getName(),
                "capability_code", t.getCapabilityCode(),
                "data_sensitivity", t.getAllowedDataSensitivity()
        )).toList());
        catalog.put("available_tools", tools.stream().map(t -> Map.of(
                "tool_code", t.getToolCode(),
                "name", t.getName(),
                "is_read_only", t.getIsReadOnly(),
                "required_permission", t.getRequiredPermission() != null ? t.getRequiredPermission() : "NONE"
        )).toList());
        catalog.put("knowledge_collections", collections.stream().map(c -> Map.of(
                "collection_code", c.collectionCode(),
                "name", c.name(),
                "archetype", c.collectionType()
        )).toList());

        return ApiResponse.ok(catalog);
    }

    @GetMapping("/sandbox/credentials")
    @Operation(summary = "List Active Sandbox Credentials (Non-Production Only)")
    public ApiResponse<List<ApplicationSandboxBootstrapService.SandboxCredentialInfo>> listSandboxCredentials(
            @AuthenticationPrincipal VerifiedRequestContext ctx
    ) {
        if ("PROD".equalsIgnoreCase(properties.environment())) {
            return ApiResponse.ok(List.of());
        }
        return ApiResponse.ok(sandboxService.getActiveSandboxCredentials());
    }
}

