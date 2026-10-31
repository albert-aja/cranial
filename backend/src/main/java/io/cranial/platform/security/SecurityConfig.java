package io.cranial.platform.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.cranial.platform.application.credential.ApplicationCredentialService;
import io.cranial.platform.audit.AuditService;
import io.cranial.platform.platform.common.ApiResponse;
import io.cranial.platform.platform.common.ErrorDetail;
import io.cranial.platform.platform.config.CranialProperties;
import io.cranial.platform.platform.exception.ErrorCode;
import io.cranial.platform.platform.observability.PlatformMetrics;
import io.cranial.platform.platform.ratelimit.RateLimiter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    @Order(1)
    public SecurityFilterChain integrationSecurityFilterChain(
            HttpSecurity http,
            ApplicationCredentialService credentialService,
            SignedContextVerifier signedContextVerifier,
            RateLimiter rateLimiter,
            AuditService auditService,
            PlatformMetrics metrics,
            ObjectMapper objectMapper
    ) throws Exception {
        AppApiKeyAuthFilter apiKeyFilter = new AppApiKeyAuthFilter(
                credentialService, signedContextVerifier, rateLimiter, auditService, metrics, objectMapper
        );

        http.securityMatcher("/api/v1/**")
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/v1/documents/transfer/**").permitAll()
                        .anyRequest().hasRole("APPLICATION")
                )
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint((req, res, ex) ->
                                writeSecurityError(res, objectMapper, ErrorCode.UNAUTHORIZED, "Application authentication required"))
                        .accessDeniedHandler((req, res, ex) ->
                                writeSecurityError(res, objectMapper, ErrorCode.FORBIDDEN, "Application access denied"))
                )
                .addFilterBefore(apiKeyFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain adminAndPlatformSecurityFilterChain(
            HttpSecurity http,
            JwtTokenService jwtTokenService,
            PlatformMetrics metrics,
            ObjectMapper objectMapper
    ) throws Exception {
        AdminJwtAuthFilter jwtFilter = new AdminJwtAuthFilter(jwtTokenService, metrics, objectMapper);

        http.csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/admin/v1/auth/login").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/api/admin/v1/**").authenticated()
                        .requestMatchers("/actuator/**").authenticated()
                        .anyRequest().denyAll()
                )
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint((req, res, ex) ->
                                writeSecurityError(res, objectMapper, ErrorCode.UNAUTHORIZED, "Operator authentication required"))
                        .accessDeniedHandler((req, res, ex) ->
                                writeSecurityError(res, objectMapper, ErrorCode.FORBIDDEN, "Insufficient operator permissions"))
                )
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(CranialProperties properties) {
        CorsConfiguration config = new CorsConfiguration();
        List<String> origins = properties.cors() != null && properties.cors().allowedOrigins() != null
                ? properties.cors().allowedOrigins().stream().filter(o -> o != null && !o.isBlank()).toList()
                : List.of("http://localhost:3000", "http://localhost:5173");
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "X-Correlation-Id",
                "X-Cranial-Api-Key",
                "X-Cranial-Context",
                "X-Cranial-Timestamp",
                "X-Cranial-Signature",
                "Idempotency-Key"
        ));
        config.setExposedHeaders(List.of(
                "X-Correlation-Id",
                "X-RateLimit-Limit",
                "X-RateLimit-Remaining",
                "X-Idempotent-Replay",
                "Retry-After"
        ));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    private static void writeSecurityError(
            HttpServletResponse response,
            ObjectMapper objectMapper,
            ErrorCode code,
            String message
    ) throws IOException {
        response.setStatus(code.httpStatus().value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiResponse<Void> body = ApiResponse.error(List.of(ErrorDetail.of(code.name(), message, code.retryable())));
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}

