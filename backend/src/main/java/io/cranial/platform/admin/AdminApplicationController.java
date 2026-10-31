package io.cranial.platform.admin;

import io.cranial.platform.application.capability.AiCapabilityEntity;
import io.cranial.platform.application.capability.CapabilityEntitlementService;
import io.cranial.platform.application.capability.CapabilityEntitlementView;
import io.cranial.platform.application.credential.ApplicationCredentialService;
import io.cranial.platform.application.credential.CredentialIssuedResult;
import io.cranial.platform.application.credential.CredentialView;
import io.cranial.platform.application.registry.ApplicationRegistryService;
import io.cranial.platform.application.registry.ApplicationView;
import io.cranial.platform.platform.common.ApiResponse;
import io.cranial.platform.platform.config.OpenApiConfig;
import io.cranial.platform.security.AdminPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/v1")
@Tag(name = "Admin Application & Capability Management (v1)", description = "Register applications, rotate/revoke credentials, and manage capability entitlements")
@SecurityRequirement(name = OpenApiConfig.SCHEME_ADMIN_JWT)
public class AdminApplicationController {

    private final ApplicationRegistryService registryService;
    private final ApplicationCredentialService credentialService;
    private final CapabilityEntitlementService capabilityService;

    public AdminApplicationController(
            ApplicationRegistryService registryService,
            ApplicationCredentialService credentialService,
            CapabilityEntitlementService capabilityService
    ) {
        this.registryService = registryService;
        this.credentialService = credentialService;
        this.capabilityService = capabilityService;
    }

    public record CreateAppRequest(
            @NotBlank @Size(min = 3, max = 80) String code,
            @NotBlank String environment,
            @NotBlank @Size(max = 150) String name,
            @Size(max = 1000) String description,
            List<String> allowedOrigins,
            Integer rateLimitRpm,
            Integer concurrencyLimit,
            Integer maxInputBytes,
            Boolean requireSignedContext
    ) {
    }

    public record UpdateStatusRequest(@NotBlank String status) {
    }

    public record IssueCredentialRequest(
            @NotBlank @Size(max = 120) String name,
            Instant expiresAt
    ) {
    }

    public record RotateCredentialRequest(Integer gracePeriodMinutes) {
    }

    public record UpdateCapabilityEntitlementRequest(
            @NotNull Boolean enabled,
            Integer quotaDailyRequests
    ) {
    }

    @GetMapping("/capabilities")
    @PreAuthorize("hasAuthority('app:read')")
    @Operation(summary = "List CRANIAL shared capability catalog")
    public ApiResponse<List<AiCapabilityEntity>> listCapabilityCatalog() {
        return ApiResponse.ok(capabilityService.listCatalog());
    }

    @GetMapping("/applications")
    @PreAuthorize("hasAuthority('app:read')")
    @Operation(summary = "List registered applications with pagination and filters")
    public ApiResponse<List<ApplicationView>> listApplications(
            @RequestParam(required = false) String environment,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return registryService.listApplications(environment, status, search, page, size);
    }

    @PostMapping("/applications")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('app:write')")
    @Operation(summary = "Register a new application in DEV, STAGING, or PROD")
    public ApiResponse<ApplicationView> createApplication(
            @AuthenticationPrincipal AdminPrincipal operator,
            @Valid @RequestBody CreateAppRequest req,
            HttpServletRequest servletRequest
    ) {
        ApplicationView created = registryService.createApplication(
                new ApplicationRegistryService.CreateApplicationCommand(
                        req.code(),
                        req.environment(),
                        req.name(),
                        req.description(),
                        req.allowedOrigins(),
                        req.rateLimitRpm(),
                        req.concurrencyLimit(),
                        req.maxInputBytes(),
                        req.requireSignedContext()
                ),
                operator.userId(),
                servletRequest.getRemoteAddr()
        );
        return ApiResponse.ok(created);
    }

    @GetMapping("/applications/{applicationId}")
    @PreAuthorize("hasAuthority('app:read')")
    @Operation(summary = "Get registered application details and settings")
    public ApiResponse<ApplicationView> getApplication(@PathVariable UUID applicationId) {
        return ApiResponse.ok(registryService.getApplication(applicationId));
    }

    @PatchMapping("/applications/{applicationId}/status")
    @PreAuthorize("hasAuthority('app:write')")
    @Operation(summary = "Update application lifecycle status (ACTIVE, SUSPENDED, RETIRED)")
    public ApiResponse<ApplicationView> updateStatus(
            @PathVariable UUID applicationId,
            @AuthenticationPrincipal AdminPrincipal operator,
            @Valid @RequestBody UpdateStatusRequest req,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.ok(registryService.updateStatus(
                applicationId, req.status(), operator.userId(), servletRequest.getRemoteAddr()
        ));
    }

    @PutMapping("/applications/{applicationId}/settings")
    @PreAuthorize("hasAuthority('app:write')")
    @Operation(summary = "Update application rate limits, payload caps, signed-context policy, and local-first provider flags")
    public ApiResponse<ApplicationView> updateSettings(
            @PathVariable UUID applicationId,
            @AuthenticationPrincipal AdminPrincipal operator,
            @Valid @RequestBody ApplicationRegistryService.UpdateApplicationSettingsCommand req,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.ok(registryService.updateSettings(
                applicationId, req, operator.userId(), servletRequest.getRemoteAddr()
        ));
    }

    @GetMapping("/applications/{applicationId}/credentials")
    @PreAuthorize("hasAuthority('app:read')")
    @Operation(summary = "List credentials for an application (prefixes & status only; never exposes secrets)")
    public ApiResponse<List<CredentialView>> listCredentials(@PathVariable UUID applicationId) {
        return ApiResponse.ok(credentialService.listForApplication(applicationId));
    }

    @PostMapping("/applications/{applicationId}/credentials")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('credential:manage')")
    @Operation(summary = "Issue a new API credential and signing secret (revealed once)")
    public ApiResponse<CredentialIssuedResult> issueCredential(
            @PathVariable UUID applicationId,
            @AuthenticationPrincipal AdminPrincipal operator,
            @Valid @RequestBody IssueCredentialRequest req,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.ok(credentialService.issueCredential(
                applicationId,
                req.name(),
                req.expiresAt(),
                operator.userId(),
                servletRequest.getRemoteAddr()
        ));
    }

    @PostMapping("/applications/{applicationId}/credentials/{credentialId}/rotate")
    @PreAuthorize("hasAuthority('credential:manage')")
    @Operation(summary = "Rotate an API credential with optional grace period (new secret revealed once)")
    public ApiResponse<CredentialIssuedResult> rotateCredential(
            @PathVariable UUID applicationId,
            @PathVariable UUID credentialId,
            @AuthenticationPrincipal AdminPrincipal operator,
            @RequestBody(required = false) RotateCredentialRequest req,
            HttpServletRequest servletRequest
    ) {
        int graceMinutes = (req != null && req.gracePeriodMinutes() != null) ? req.gracePeriodMinutes() : 60;
        return ApiResponse.ok(credentialService.rotateCredential(
                applicationId,
                credentialId,
                graceMinutes,
                operator.userId(),
                servletRequest.getRemoteAddr()
        ));
    }

    @PostMapping("/applications/{applicationId}/credentials/{credentialId}/revoke")
    @PreAuthorize("hasAuthority('credential:manage')")
    @Operation(summary = "Immediately revoke an API credential")
    public ApiResponse<CredentialView> revokeCredential(
            @PathVariable UUID applicationId,
            @PathVariable UUID credentialId,
            @AuthenticationPrincipal AdminPrincipal operator,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.ok(credentialService.revokeCredential(
                applicationId,
                credentialId,
                operator.userId(),
                servletRequest.getRemoteAddr()
        ));
    }

    @GetMapping("/applications/{applicationId}/capabilities")
    @PreAuthorize("hasAuthority('app:read')")
    @Operation(summary = "List capability entitlements and daily quotas for an application")
    public ApiResponse<List<CapabilityEntitlementView>> listApplicationCapabilities(
            @PathVariable UUID applicationId
    ) {
        return ApiResponse.ok(capabilityService.listApplicationEntitlements(applicationId));
    }

    @PutMapping("/applications/{applicationId}/capabilities/{capabilityCode}")
    @PreAuthorize("hasAuthority('capability:manage')")
    @Operation(summary = "Enable/disable a capability entitlement and set daily request quota for an application")
    public ApiResponse<CapabilityEntitlementView> updateCapabilityEntitlement(
            @PathVariable UUID applicationId,
            @PathVariable String capabilityCode,
            @AuthenticationPrincipal AdminPrincipal operator,
            @Valid @RequestBody UpdateCapabilityEntitlementRequest req,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.ok(capabilityService.updateEntitlement(
                applicationId,
                capabilityCode,
                req.enabled(),
                req.quotaDailyRequests(),
                operator.userId(),
                servletRequest.getRemoteAddr()
        ));
    }
}

