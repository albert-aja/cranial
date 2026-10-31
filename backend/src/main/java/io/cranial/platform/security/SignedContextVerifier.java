package io.cranial.platform.security;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cranial.platform.application.credential.VerifiedApplicationCredential;
import io.cranial.platform.application.tenant.TenantEntity;
import io.cranial.platform.application.tenant.TenantService;
import io.cranial.platform.platform.config.CranialProperties;
import io.cranial.platform.platform.crypto.CryptoService;
import io.cranial.platform.platform.exception.CranialException;
import io.cranial.platform.platform.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Component
public class SignedContextVerifier {

    public static final String HEADER_CONTEXT = "X-Cranial-Context";
    public static final String HEADER_TIMESTAMP = "X-Cranial-Timestamp";
    public static final String HEADER_SIGNATURE = "X-Cranial-Signature";

    private final CryptoService cryptoService;
    private final TenantService tenantService;
    private final CranialProperties properties;
    private final ObjectMapper objectMapper;

    public SignedContextVerifier(
            CryptoService cryptoService,
            TenantService tenantService,
            CranialProperties properties,
            ObjectMapper objectMapper
    ) {
        this.cryptoService = cryptoService;
        this.tenantService = tenantService;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public record SignedContextClaims(
            @JsonAlias({"tenantRef", "tenant_ref"}) String tenantRef,
            @JsonAlias({"userRef", "user_ref"}) String userRef,
            List<String> roles,
            List<String> scopes
    ) {
    }

    public record VerifiedIdentityScope(
            boolean signed,
            UUID tenantId,
            String externalTenantRef,
            String externalUserRef,
            List<String> roles,
            List<String> scopes
    ) {
        public static VerifiedIdentityScope unsigned() {
            return new VerifiedIdentityScope(false, null, null, null, List.of(), List.of());
        }
    }

    public VerifiedIdentityScope verifyIfPresent(
            VerifiedApplicationCredential cred,
            String method,
            String requestPath,
            byte[] rawBody,
            String contextHeader,
            String timestampHeader,
            String signatureHeader,
            String ipAddress
    ) {
        boolean anyHeaderPresent = (contextHeader != null && !contextHeader.isBlank())
                || (timestampHeader != null && !timestampHeader.isBlank())
                || (signatureHeader != null && !signatureHeader.isBlank());

        if (!anyHeaderPresent) {
            if (isExemptFromSignedContextRequirement(requestPath)) {
                return VerifiedIdentityScope.unsigned();
            }
            if (cred.requireSignedContext()) {
                throw new CranialException(
                        ErrorCode.MISSING_SIGNED_CONTEXT,
                        "Application policy requires signed X-Cranial-Context, X-Cranial-Timestamp, and X-Cranial-Signature headers"
                );
            }
            return VerifiedIdentityScope.unsigned();
        }

        if (contextHeader == null || contextHeader.isBlank()
                || timestampHeader == null || timestampHeader.isBlank()
                || signatureHeader == null || signatureHeader.isBlank()) {
            throw new CranialException(
                    ErrorCode.INVALID_CONTEXT_SIGNATURE,
                    "Incomplete signed context headers: X-Cranial-Context, X-Cranial-Timestamp, and X-Cranial-Signature are all required"
            );
        }

        long epochSeconds;
        try {
            epochSeconds = Long.parseLong(timestampHeader.trim());
        } catch (NumberFormatException ex) {
            throw new CranialException(ErrorCode.INVALID_CONTEXT_SIGNATURE, "Invalid X-Cranial-Timestamp epoch value");
        }

        long nowSeconds = Instant.now().getEpochSecond();
        long maxSkew = properties.security().contextMaxSkewSeconds();
        if (Math.abs(nowSeconds - epochSeconds) > maxSkew) {
            throw new CranialException(
                    ErrorCode.CONTEXT_TIMESTAMP_EXPIRED,
                    "X-Cranial-Timestamp outside allowed replay window"
            );
        }

        String bodySha256 = cryptoService.sha256Hex(rawBody != null ? rawBody : new byte[0]);
        String canonicalString = timestampHeader.trim() + "."
                + method.toUpperCase() + "."
                + requestPath + "."
                + contextHeader.trim() + "."
                + bodySha256;

        String expectedSignature = cryptoService.hmacSha256Hex(cred.signingSecret(), canonicalString);
        if (!cryptoService.constantTimeEquals(expectedSignature, signatureHeader.trim().toLowerCase())) {
            throw new CranialException(ErrorCode.INVALID_CONTEXT_SIGNATURE, "X-Cranial-Signature verification failed");
        }

        SignedContextClaims claims;
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(contextHeader.trim());
            claims = objectMapper.readValue(new String(decoded, StandardCharsets.UTF_8), SignedContextClaims.class);
        } catch (Exception ex) {
            throw new CranialException(ErrorCode.INVALID_CONTEXT_SIGNATURE, "Malformed Base64URL X-Cranial-Context payload");
        }

        UUID verifiedTenantId = null;
        String verifiedTenantRef = null;
        if (claims.tenantRef() != null && !claims.tenantRef().isBlank()) {
            TenantEntity tenant = tenantService.verifyAuthorizedTenant(
                    cred.applicationId(),
                    cred.applicationCode(),
                    claims.tenantRef().trim(),
                    claims.userRef(),
                    ipAddress
            );
            verifiedTenantId = tenant.getId();
            verifiedTenantRef = tenant.getExternalTenantRef();
        }

        return new VerifiedIdentityScope(
                true,
                verifiedTenantId,
                verifiedTenantRef,
                claims.userRef() != null && !claims.userRef().isBlank() ? claims.userRef().trim() : null,
                claims.roles() != null ? List.copyOf(claims.roles()) : List.of(),
                claims.scopes() != null ? List.copyOf(claims.scopes()) : List.of()
        );
    }

    private boolean isExemptFromSignedContextRequirement(String path) {
        if (path == null) {
            return false;
        }
        return path.startsWith("/api/v1/integration/health")
                || path.startsWith("/api/v1/integration/catalog")
                || path.startsWith("/api/v1/documents/transfer");
    }
}

