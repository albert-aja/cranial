package io.cranial.platform.security;

import io.cranial.platform.platform.config.CranialProperties;
import io.cranial.platform.platform.exception.CranialException;
import io.cranial.platform.platform.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
public class JwtTokenService {

    private final SecretKey signingKey;
    private final Duration ttl;

    public JwtTokenService(CranialProperties properties) {
        byte[] keyBytes = derive256BitKey(properties.security().adminJwtSecret());
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.ttl = Duration.ofMinutes(properties.security().adminJwtTtlMinutes());
    }

    public record IssuedAdminToken(String accessToken, Instant expiresAt) {
    }

    public IssuedAdminToken issueToken(AdminPrincipal principal) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(ttl);
        String token = Jwts.builder()
                .subject(principal.userId().toString())
                .issuer("cranial-platform")
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .claim("email", principal.email())
                .claim("name", principal.displayName())
                .claim("roles", principal.roles())
                .claim("perms", principal.permissions())
                .signWith(signingKey)
                .compact();
        return new IssuedAdminToken(token, expiresAt);
    }

    @SuppressWarnings("unchecked")
    public AdminPrincipal verifyToken(String rawJwt) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer("cranial-platform")
                    .build()
                    .parseSignedClaims(rawJwt)
                    .getPayload();

            UUID userId = UUID.fromString(claims.getSubject());
            String email = claims.get("email", String.class);
            String name = claims.get("name", String.class);
            List<String> roles = claims.get("roles", List.class);
            List<String> perms = claims.get("perms", List.class);

            return new AdminPrincipal(
                    userId,
                    email,
                    name,
                    roles != null ? List.copyOf(roles) : List.of(),
                    perms != null ? List.copyOf(perms) : List.of()
            );
        } catch (JwtException | IllegalArgumentException ex) {
            throw new CranialException(ErrorCode.INVALID_CREDENTIALS, "Invalid or expired operator access token");
        }
    }

    private static byte[] derive256BitKey(String secret) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}

