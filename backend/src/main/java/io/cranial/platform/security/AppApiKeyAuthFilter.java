package io.cranial.platform.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.cranial.platform.application.credential.ApplicationCredentialService;
import io.cranial.platform.application.credential.VerifiedApplicationCredential;
import io.cranial.platform.audit.AuditEvent;
import io.cranial.platform.audit.AuditService;
import io.cranial.platform.platform.common.ApiResponse;
import io.cranial.platform.platform.common.ErrorDetail;
import io.cranial.platform.platform.exception.CranialException;
import io.cranial.platform.platform.exception.ErrorCode;
import io.cranial.platform.platform.observability.CorrelationIdHolder;
import io.cranial.platform.platform.observability.PlatformMetrics;
import io.cranial.platform.platform.ratelimit.RateLimitDecision;
import io.cranial.platform.platform.ratelimit.RateLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public class AppApiKeyAuthFilter extends OncePerRequestFilter {

    public static final String HEADER_API_KEY = "X-Cranial-Api-Key";

    private final ApplicationCredentialService credentialService;
    private final SignedContextVerifier signedContextVerifier;
    private final RateLimiter rateLimiter;
    private final AuditService auditService;
    private final PlatformMetrics metrics;
    private final ObjectMapper objectMapper;

    public AppApiKeyAuthFilter(
            ApplicationCredentialService credentialService,
            SignedContextVerifier signedContextVerifier,
            RateLimiter rateLimiter,
            AuditService auditService,
            PlatformMetrics metrics,
            ObjectMapper objectMapper
    ) {
        this.credentialService = credentialService;
        this.signedContextVerifier = signedContextVerifier;
        this.rateLimiter = rateLimiter;
        this.auditService = auditService;
        this.metrics = metrics;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path == null
                || !path.startsWith("/api/v1/")
                || path.startsWith("/api/v1/documents/transfer/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String ip = request.getRemoteAddr();
        VerifiedApplicationCredential cred = null;
        try {
            String rawApiKey = request.getHeader(HEADER_API_KEY);
            cred = credentialService.verifyApiKey(rawApiKey);
            MDC.put(CorrelationIdHolder.MDC_APP_KEY, cred.applicationCode());

            RateLimitDecision rl = rateLimiter.tryAcquire("app:" + cred.applicationId(), cred.rateLimitRpm());
            response.setHeader("X-RateLimit-Limit", String.valueOf(rl.limitRpm()));
            response.setHeader("X-RateLimit-Remaining", String.valueOf(rl.remaining()));
            if (!rl.allowed()) {
                metrics.recordRateLimitRejection(cred.applicationCode());
                auditService.record(new AuditEvent(
                        "APPLICATION",
                        cred.applicationCode(),
                        cred.applicationId(),
                        null,
                        "RATE_LIMIT_EXCEEDED",
                        "GATEWAY",
                        request.getRequestURI(),
                        "DENIED",
                        Map.of("limit_rpm", rl.limitRpm()),
                        ip
                ));
                throw new CranialException(
                        ErrorCode.RATE_LIMIT_EXCEEDED,
                        "Application rate limit exceeded (" + rl.limitRpm() + " requests/min)",
                        rl.retryAfterSeconds()
                );
            }

            boolean isMultipart = request.getContentType() != null
                    && request.getContentType().toLowerCase().startsWith("multipart/");

            HttpServletRequest downstreamRequest;
            byte[] bodyForSignature;
            if (isMultipart) {
                if (request.getContentLengthLong() > cred.maxInputBytes()) {
                    throw new CranialException(
                            ErrorCode.PAYLOAD_TOO_LARGE,
                            "Request payload exceeds application max_input_bytes limit (" + cred.maxInputBytes() + " bytes)"
                    );
                }
                downstreamRequest = request;
                bodyForSignature = new byte[0];
            } else {
                CachedBodyHttpServletRequest wrappedRequest = new CachedBodyHttpServletRequest(request, cred.maxInputBytes());
                downstreamRequest = wrappedRequest;
                bodyForSignature = wrappedRequest.cachedBody();
            }

            SignedContextVerifier.VerifiedIdentityScope scope = signedContextVerifier.verifyIfPresent(
                    cred,
                    downstreamRequest.getMethod(),
                    downstreamRequest.getRequestURI(),
                    bodyForSignature,
                    downstreamRequest.getHeader(SignedContextVerifier.HEADER_CONTEXT),
                    downstreamRequest.getHeader(SignedContextVerifier.HEADER_TIMESTAMP),
                    downstreamRequest.getHeader(SignedContextVerifier.HEADER_SIGNATURE),
                    ip
            );

            VerifiedRequestContext verifiedContext = new VerifiedRequestContext(
                    cred.applicationId(),
                    cred.applicationCode(),
                    cred.environment(),
                    cred.credentialId(),
                    cred.keyPrefix(),
                    scope.signed(),
                    scope.tenantId(),
                    scope.externalTenantRef(),
                    scope.externalUserRef(),
                    scope.roles(),
                    scope.scopes(),
                    CorrelationIdHolder.currentOrGenerate(),
                    cred.allowHostedProviders(),
                    cred.allowSensitiveExternal()
            );

            SecurityContextHolder.getContext().setAuthentication(new ApplicationAuthenticationToken(verifiedContext));
            metrics.recordAuthDecision("integration", "success");
            filterChain.doFilter(downstreamRequest, response);
        } catch (CranialException ex) {
            metrics.recordAuthDecision("integration", "denied_" + ex.errorCode().name().toLowerCase());
            if (ex.errorCode() != ErrorCode.RATE_LIMIT_EXCEEDED && ex.errorCode() != ErrorCode.TENANT_NOT_AUTHORIZED) {
                auditService.record(new AuditEvent(
                        cred != null ? "APPLICATION" : "ANONYMOUS",
                        cred != null ? cred.applicationCode() : "unauthenticated",
                        cred != null ? cred.applicationId() : null,
                        null,
                        "INTEGRATION_AUTH_DENIED",
                        "GATEWAY",
                        request.getRequestURI(),
                        "DENIED",
                        Map.of("error_code", ex.errorCode().name(), "reason", ex.getMessage()),
                        ip
                ));
            }
            writeErrorResponse(response, ex);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void writeErrorResponse(HttpServletResponse response, CranialException ex) throws IOException {
        ErrorCode code = ex.errorCode();
        response.setStatus(code.httpStatus().value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        if (ex.retryAfterSeconds() != null) {
            response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(ex.retryAfterSeconds()));
        }
        ApiResponse<Void> body = ApiResponse.error(
                List.of(ErrorDetail.of(code.name(), ex.getMessage(), code.retryable()))
        );
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}

