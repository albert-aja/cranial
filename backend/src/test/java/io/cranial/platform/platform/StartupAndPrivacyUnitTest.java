package io.cranial.platform.platform;

import io.cranial.platform.platform.config.CranialProperties;
import io.cranial.platform.platform.config.StartupConfigValidator;
import io.cranial.platform.platform.crypto.CryptoService;
import io.cranial.platform.platform.observability.LogSanitizer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StartupAndPrivacyUnitTest {

    @Test
    void startupValidatorRejectsShortSecretsAndDevPlaceholdersInProd() {
        CranialProperties shortSecretProps = new CranialProperties(
                "dev",
                new CranialProperties.CorsProperties(List.of()),
                new CranialProperties.SecurityProperties("too-short", "x".repeat(32), "y".repeat(32), 30, 300, 10),
                null
        );
        assertThatThrownBy(() -> StartupConfigValidator.validate(shortSecretProps))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("api-key-pepper");

        CranialProperties prodPlaceholderProps = new CranialProperties(
                "prod",
                new CranialProperties.CorsProperties(List.of()),
                new CranialProperties.SecurityProperties(
                        "dev-only-api-key-pepper-replace-in-prod-32bytes-min",
                        "strong-random-prod-encryption-key-value-9999",
                        "strong-random-prod-admin-jwt-key-value-99999",
                        30,
                        300,
                        10
                ),
                null
        );
        assertThatThrownBy(() -> StartupConfigValidator.validate(prodPlaceholderProps))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("forbidden in prod");

        CranialProperties validProdProps = new CranialProperties(
                "prod",
                new CranialProperties.CorsProperties(List.of()),
                new CranialProperties.SecurityProperties(
                        "9f8e7d6c5b4a39281706f5e4d3c2b1a09f8e7d6c",
                        "1a2b3c4d5e6f708192a3b4c5d6e7f8091a2b3c4d",
                        "a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4",
                        30,
                        300,
                        10
                ),
                null
        );
        StartupConfigValidator.validate(validProdProps);
    }

    @Test
    void cryptoServiceEncryptsWithRandomIvAndVerifiesHmacInConstantTime() {
        CranialProperties props = new CranialProperties(
                "test",
                new CranialProperties.CorsProperties(List.of()),
                new CranialProperties.SecurityProperties(
                        "9f8e7d6c5b4a39281706f5e4d3c2b1a09f8e7d6c",
                        "1a2b3c4d5e6f708192a3b4c5d6e7f8091a2b3c4d",
                        "a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4",
                        30,
                        300,
                        10
                ),
                null
        );
        CryptoService crypto = new CryptoService(props);

        String c1 = crypto.encryptSecret("super-signing-secret");
        String c2 = crypto.encryptSecret("super-signing-secret");
        assertThat(c1).isNotEqualTo(c2);
        assertThat(crypto.decryptSecret(c1)).isEqualTo("super-signing-secret");
        assertThat(crypto.decryptSecret(c2)).isEqualTo("super-signing-secret");

        String hash = crypto.hashApiKeySecret("raw-secret-token");
        assertThat(crypto.verifyApiKeySecret("raw-secret-token", hash)).isTrue();
        assertThat(crypto.verifyApiKeySecret("wrong-secret-token", hash)).isFalse();
    }

    @Test
    void logSanitizerRedactsSecretsInlineTokensAndNestedMaps() {
        Map<String, Object> unsafe = Map.of(
                "application", "peggy",
                "password", "SuperSecret123!",
                "X-Cranial-Api-Key", "crn_prod_abcdefgh2345.secretValue999",
                "nested", Map.of(
                        "webhook_secret", "whsec_live_123",
                        "note", "Header was crn_dev_xyz987654321.myRawTokenValue and Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.sig123"
                )
        );

        Map<String, Object> clean = LogSanitizer.sanitizeMap(unsafe);
        assertThat(clean.get("application")).isEqualTo("peggy");
        assertThat(clean.get("password")).isEqualTo(LogSanitizer.REDACTED);
        assertThat(clean.get("X-Cranial-Api-Key")).isEqualTo(LogSanitizer.REDACTED);

        @SuppressWarnings("unchecked")
        Map<String, Object> nested = (Map<String, Object>) clean.get("nested");
        assertThat(nested.get("webhook_secret")).isEqualTo(LogSanitizer.REDACTED);
        assertThat(nested.get("note").toString())
                .doesNotContain("myRawTokenValue")
                .doesNotContain("eyJhbGciOiJIUzI1NiJ9")
                .contains("crn_[REDACTED]")
                .contains("Bearer [REDACTED]");
    }
}

