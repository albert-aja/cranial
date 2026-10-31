package io.cranial.platform.admin;

import io.cranial.platform.audit.AuditLogView;
import io.cranial.platform.audit.AuditService;
import io.cranial.platform.platform.common.ApiResponse;
import io.cranial.platform.platform.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/v1/audit-logs")
@Tag(name = "Admin Audit Operations (v1)", description = "Search sanitized platform authorization, credential, and governance audit logs")
@SecurityRequirement(name = OpenApiConfig.SCHEME_ADMIN_JWT)
public class AdminAuditController {

    private final AuditService auditService;

    public AdminAuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('audit:read')")
    @Operation(summary = "Search paginated platform audit logs")
    public ApiResponse<List<AuditLogView>> searchAuditLogs(
            @RequestParam(required = false) UUID applicationId,
            @RequestParam(required = false) String outcome,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String correlationId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "25") int size
    ) {
        return auditService.search(applicationId, outcome, action, correlationId, page, size);
    }
}

