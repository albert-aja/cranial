package io.cranial.platform.admin;

import io.cranial.platform.platform.common.ApiResponse;
import io.cranial.platform.platform.config.OpenApiConfig;
import io.cranial.platform.security.AdminPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/v1/auth")
@Tag(name = "Admin Authentication (v1)", description = "Platform operator authentication and session inspection")
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    public AdminAuthController(AdminAuthService adminAuthService) {
        this.adminAuthService = adminAuthService;
    }

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password
    ) {
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate platform operator and issue short-lived JWT")
    public ApiResponse<AdminAuthService.LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.ok(adminAuthService.login(
                request.email(),
                request.password(),
                servletRequest.getRemoteAddr()
        ));
    }

    @GetMapping("/me")
    @Operation(summary = "Return current authenticated operator profile, roles, and permissions")
    @SecurityRequirement(name = OpenApiConfig.SCHEME_ADMIN_JWT)
    public ApiResponse<AdminPrincipal> me(@AuthenticationPrincipal AdminPrincipal principal) {
        return ApiResponse.ok(principal);
    }
}

