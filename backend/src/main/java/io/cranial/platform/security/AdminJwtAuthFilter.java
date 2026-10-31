package io.cranial.platform.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.cranial.platform.platform.common.ApiResponse;
import io.cranial.platform.platform.common.ErrorDetail;
import io.cranial.platform.platform.exception.CranialException;
import io.cranial.platform.platform.observability.PlatformMetrics;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class AdminJwtAuthFilter extends OncePerRequestFilter {

    private final JwtTokenService jwtTokenService;
    private final PlatformMetrics metrics;
    private final ObjectMapper objectMapper;

    public AdminJwtAuthFilter(
            JwtTokenService jwtTokenService,
            PlatformMetrics metrics,
            ObjectMapper objectMapper
    ) {
        this.jwtTokenService = jwtTokenService;
        this.metrics = metrics;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null) {
            return true;
        }
        return path.startsWith("/api/v1/") || "/api/admin/v1/auth/login".equals(path);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String rawToken = authHeader.substring(7).trim();
        try {
            AdminPrincipal principal = jwtTokenService.verifyToken(rawToken);
            List<GrantedAuthority> authorities = new ArrayList<>();
            principal.roles().forEach(r -> authorities.add(new SimpleGrantedAuthority("ROLE_" + r)));
            principal.permissions().forEach(p -> authorities.add(new SimpleGrantedAuthority(p)));

            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(principal, "[REDACTED]", authorities);
            SecurityContextHolder.getContext().setAuthentication(auth);
            metrics.recordAuthDecision("admin", "success");
            filterChain.doFilter(request, response);
        } catch (CranialException ex) {
            metrics.recordAuthDecision("admin", "denied_invalid_jwt");
            response.setStatus(ex.errorCode().httpStatus().value());
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            ApiResponse<Void> body = ApiResponse.error(
                    List.of(ErrorDetail.of(ex.errorCode().name(), ex.getMessage(), false))
            );
            response.getWriter().write(objectMapper.writeValueAsString(body));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}

