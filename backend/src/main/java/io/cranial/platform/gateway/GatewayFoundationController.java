package io.cranial.platform.gateway;

import io.cranial.platform.application.capability.CapabilityEntitlementService;
import io.cranial.platform.application.capability.CapabilityEntitlementView;
import io.cranial.platform.application.idempotency.IdempotencyService;
import io.cranial.platform.audit.AuditEvent;
import io.cranial.platform.audit.AuditService;
import io.cranial.platform.platform.common.ApiResponse;
import io.cranial.platform.platform.common.CapabilityEnvelope;
import io.cranial.platform.platform.common.CapabilityRequest;
import io.cranial.platform.platform.config.OpenApiConfig;
import io.cranial.platform.platform.exception.CranialException;
import io.cranial.platform.platform.exception.ErrorCode;
import io.cranial.platform.security.VerifiedRequestContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Integration Gateway (v1)", description = "Authenticated application integration endpoints")
@SecurityRequirement(name = OpenApiConfig.SCHEME_APP_API_KEY)
public class GatewayFoundationController {

    private final CapabilityEntitlementService capabilityService;
    private final IdempotencyService idempotencyService;
    private final AuditService auditService;

    public GatewayFoundationController(
            CapabilityEntitlementService capabilityService,
            IdempotencyService idempotencyService,
            AuditService auditService
    ) {
        this.capabilityService = capabilityService;
        this.idempotencyService = idempotencyService;
        this.auditService = auditService;
    }

    @GetMapping("/gateway/context")
    @Operation(summary = "Inspect verified application and tenant/user context")
    public ApiResponse<Map<String, Object>> inspectVerifiedContext(
            @AuthenticationPrincipal VerifiedRequestContext ctx
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("application_id", ctx.applicationId());
        payload.put("application_code", ctx.applicationCode());
        payload.put("environment", ctx.environment());
        payload.put("key_prefix", ctx.keyPrefix());
        payload.put("context_signed", ctx.contextSigned());
        payload.put("tenant_id", ctx.tenantId());
        payload.put("external_tenant_ref", ctx.externalTenantRef());
        payload.put("external_user_ref", ctx.externalUserRef());
        payload.put("roles", ctx.roles());
        payload.put("scopes", ctx.scopes());
        payload.put("local_first_policy", Map.of(
                "allow_hosted_providers", ctx.allowHostedProviders(),
                "allow_sensitive_external", ctx.allowSensitiveExternal(),
                "automatic_paid_fallback", false
        ));
        return ApiResponse.ok(payload);
    }

    @GetMapping("/capabilities")
    @Operation(summary = "List capabilities entitled for the authenticated application")
    public ApiResponse<List<CapabilityEntitlementView>> listEntitledCapabilities(
            @AuthenticationPrincipal VerifiedRequestContext ctx
    ) {
        return ApiResponse.ok(capabilityService.listEnabledForApplication(ctx.applicationId()));
    }

    @PostMapping("/gateway/verify-request")
    @Operation(summary = "Validate a standard CRANIAL capability request envelope, anti-spoofing rules, entitlement gate, and idempotency")
    @SuppressWarnings("unchecked")
    public CapabilityEnvelope<Map<String, Object>> verifyCapabilityRequest(
            @AuthenticationPrincipal VerifiedRequestContext ctx,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CapabilityRequest requestBody,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse
    ) {
        String ip = servletRequest.getRemoteAddr();

        // Anti-spoofing rule: body application_id must match verified credential's applicationCode
        if (!ctx.applicationCode().equalsIgnoreCase(requestBody.applicationId().trim())) {
            auditService.record(new AuditEvent(
                    "APPLICATION",
                    ctx.applicationCode(),
                    ctx.applicationId(),
                    ctx.tenantId(),
                    "APP_IDENTITY_SPOOF_REJECTED",
                    "GATEWAY",
                    requestBody.applicationId(),
                    "DENIED",
                    Map.of("claimed_application_id", requestBody.applicationId(), "verified_application_code", ctx.applicationCode()),
                    ip
            ));
            throw new CranialException(
                    ErrorCode.APP_IDENTITY_MISMATCH,
                    "Request application_id '" + requestBody.applicationId() + "' does not match authenticated application '" + ctx.applicationCode() + "'"
            );
        }

        // Anti-spoofing rule: reject unverified identity claims stuffed into request.context unless signed context verified
        if (!ctx.contextSigned() && requestBody.context() != null) {
            if (requestBody.context().containsKey("tenant_id")
                    || requestBody.context().containsKey("tenant_ref")
                    || requestBody.context().containsKey("user_id")
                    || requestBody.context().containsKey("roles")
                    || requestBody.context().containsKey("permissions")) {
                auditService.record(new AuditEvent(
                        "APPLICATION",
                        ctx.applicationCode(),
                        ctx.applicationId(),
                        null,
                        "UNVERIFIED_BODY_IDENTITY_REJECTED",
                        "GATEWAY",
                        requestBody.task(),
                        "DENIED",
                        Map.of("capability", requestBody.capability()),
                        ip
                ));
                throw new CranialException(
                        ErrorCode.INVALID_CONTEXT_SIGNATURE,
                        "Identity, tenant, role, and permission claims must be supplied via signed X-Cranial-Context headers, never unverified request body fields"
                );
            }
        }

        CapabilityEntitlementView entitlement = capabilityService.assertEntitled(
                ctx.applicationId(),
                requestBody.capability(),
                ip
        );

        IdempotencyService.IdempotentExecution<CapabilityEnvelope> exec = idempotencyService.execute(
                ctx.applicationId(),
                ctx.applicationCode(),
                idempotencyKey,
                "/api/v1/gateway/verify-request",
                requestBody,
                CapabilityEnvelope.class,
                () -> {
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("gateway_ready", true);
                    data.put("verified_application", ctx.applicationCode());
                    data.put("environment", ctx.environment());
                    data.put("entitled_capability", entitlement.capabilityCode());
                    data.put("quota_daily_requests", entitlement.quotaDailyRequests());
                    data.put("context_signed", ctx.contextSigned());
                    data.put("verified_tenant_ref", ctx.externalTenantRef());
                    data.put("verified_user_ref", ctx.externalUserRef());

                    Map<String, Object> metadata = new LinkedHashMap<>();
                    metadata.put("phase", "phase_1_foundation");
                    metadata.put("automatic_paid_fallback", false);
                    metadata.put("local_first", !ctx.allowHostedProviders());

                    auditService.record(new AuditEvent(
                            "APPLICATION",
                            ctx.applicationCode(),
                            ctx.applicationId(),
                            ctx.tenantId(),
                            "CAPABILITY_ENVELOPE_VERIFIED",
                            "CAPABILITY",
                            entitlement.capabilityCode(),
                            "SUCCESS",
                            Map.of("task", requestBody.task(), "schema_version", requestBody.schemaVersion()),
                            ip
                    ));

                    return CapabilityEnvelope.completed(
                            requestBody.requestId(),
                            ctx.applicationCode(),
                            entitlement.capabilityCode(),
                            requestBody.task(),
                            requestBody.schemaVersion(),
                            data,
                            List.of("Phase 1 foundation envelope validated; model orchestration attaches in Phase 2/3."),
                            metadata
                    );
                }
        );

        servletResponse.setHeader("X-Idempotent-Replay", String.valueOf(exec.replayed()));
        return exec.body();
    }
}

