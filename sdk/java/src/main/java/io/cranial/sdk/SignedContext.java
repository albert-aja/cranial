package io.cranial.sdk;

import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

public class SignedContext {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String tenantRef;
    private final String userRef;
    private final List<String> roles;
    private final List<String> scopes;

    private SignedContext(String tenantRef, String userRef, List<String> roles, List<String> scopes) {
        this.tenantRef = tenantRef;
        this.userRef = userRef;
        this.roles = roles != null ? List.copyOf(roles) : List.of();
        this.scopes = scopes != null ? List.copyOf(scopes) : List.of();
    }

    public static Builder builder() {
        return new Builder();
    }

    public String tenantRef() {
        return tenantRef;
    }

    public String userRef() {
        return userRef;
    }

    public List<String> roles() {
        return roles;
    }

    public List<String> scopes() {
        return scopes;
    }

    public record SignedHeaders(
            String contextHeader,
            String timestampHeader,
            String signatureHeader
    ) {}

    public SignedHeaders generateHeaders(String method, String path, byte[] rawBody, String signingSecret) {
        if (signingSecret == null || signingSecret.isBlank()) {
            throw new IllegalStateException("signingSecret is required to generate signed context headers");
        }

        try {
            Map<String, Object> claims = new LinkedHashMap<>();
            if (tenantRef != null) claims.put("tenantRef", tenantRef);
            if (userRef != null) claims.put("userRef", userRef);
            claims.put("roles", roles);
            claims.put("scopes", scopes);

            String claimsJson = MAPPER.writeValueAsString(claims);
            String contextHeader = Base64.getUrlEncoder().withoutPadding().encodeToString(claimsJson.getBytes(StandardCharsets.UTF_8));
            String timestampHeader = String.valueOf(Instant.now().getEpochSecond());

            String bodySha256 = sha256Hex(rawBody != null ? rawBody : new byte[0]);
            String canonicalString = timestampHeader + "."
                    + method.toUpperCase(Locale.ROOT) + "."
                    + path + "."
                    + contextHeader + "."
                    + bodySha256;

            String signatureHeader = hmacSha256Hex(signingSecret, canonicalString);

            return new SignedHeaders(contextHeader, timestampHeader, signatureHeader);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to generate signed context headers", ex);
        }
    }

    private static String sha256Hex(byte[] input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input);
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String hmacSha256Hex(String secret, String message) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            byte[] digest = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new RuntimeException("HMAC computation failed", ex);
        }
    }

    public static class Builder {
        private String tenantRef;
        private String userRef;
        private List<String> roles = new ArrayList<>();
        private List<String> scopes = new ArrayList<>();

        public Builder tenantRef(String tenantRef) {
            this.tenantRef = tenantRef;
            return this;
        }

        public Builder userRef(String userRef) {
            this.userRef = userRef;
            return this;
        }

        public Builder roles(List<String> roles) {
            if (roles != null) this.roles = new ArrayList<>(roles);
            return this;
        }

        public Builder addRole(String role) {
            if (role != null) this.roles.add(role);
            return this;
        }

        public Builder scopes(List<String> scopes) {
            if (scopes != null) this.scopes = new ArrayList<>(scopes);
            return this;
        }

        public Builder addScope(String scope) {
            if (scope != null) this.scopes.add(scope);
            return this;
        }

        public SignedContext build() {
            return new SignedContext(tenantRef, userRef, roles, scopes);
        }
    }
}

