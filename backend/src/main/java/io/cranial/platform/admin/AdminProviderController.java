package io.cranial.platform.admin;

import io.cranial.platform.platform.common.ApiResponse;
import io.cranial.platform.platform.config.OpenApiConfig;
import io.cranial.platform.provider.model.ProviderHealth;
import io.cranial.platform.provider.model.ProviderView;
import io.cranial.platform.provider.service.ProviderService;
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
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/v1/providers")
@Tag(name = "Admin Provider Management", description = "Register and monitor AI execution runtimes and health")
@SecurityRequirement(name = OpenApiConfig.SCHEME_ADMIN_JWT)
public class AdminProviderController {

    private final ProviderService providerService;

    public AdminProviderController(ProviderService providerService) {
        this.providerService = providerService;
    }

    @GetMapping
    @Operation(summary = "List all registered AI providers")
    public ApiResponse<List<ProviderView>> listProviders() {
        return ApiResponse.ok(providerService.listProviders());
    }

    @GetMapping("/{id}/health")
    @Operation(summary = "Perform real-time health check on provider")
    public ApiResponse<ProviderHealth> checkHealth(@PathVariable UUID id) {
        return ApiResponse.ok(providerService.checkProviderHealth(id));
    }

    @PostMapping("/{id}/circuit/reset")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'PLATFORM_OPERATOR')")
    @Operation(summary = "Manually reset provider circuit breaker back to CLOSED state")
    public ApiResponse<Map<String, Object>> resetCircuitBreaker(@PathVariable UUID id) {
        providerService.resetCircuitBreaker(id);
        return ApiResponse.ok(Map.of("message", "Circuit breaker reset to CLOSED"));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'PLATFORM_OPERATOR')")
    @Operation(summary = "Create or update AI provider runtime")
    public ApiResponse<ProviderView> saveProvider(@Valid @RequestBody SaveProviderRequest req) {
        ProviderView view = providerService.saveProvider(
                req.id(),
                req.code(),
                req.name(),
                req.providerType(),
                req.baseUrl(),
                req.status(),
                req.timeoutSeconds() != null ? req.timeoutSeconds() : 30,
                req.configJson()
        );
        return ApiResponse.ok(view);
    }

    public record SaveProviderRequest(
            UUID id,
            @NotBlank String code,
            @NotBlank String name,
            @NotBlank @com.fasterxml.jackson.annotation.JsonAlias({"providerType", "provider_type"}) String providerType,
            @NotBlank @com.fasterxml.jackson.annotation.JsonAlias({"baseUrl", "base_url"}) String baseUrl,
            String status,
            @com.fasterxml.jackson.annotation.JsonAlias({"timeoutSeconds", "timeout_seconds"}) Integer timeoutSeconds,
            @com.fasterxml.jackson.annotation.JsonAlias({"configJson", "config_json"}) String configJson
    ) {
    }
}
