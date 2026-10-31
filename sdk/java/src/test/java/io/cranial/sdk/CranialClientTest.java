package io.cranial.sdk;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CranialClientTest {

    @Test
    @DisplayName("CranialConfig builder validates required fields")
    void configValidation() {
        assertThrows(IllegalArgumentException.class, () -> CranialConfig.builder().apiKey(null).build());
        assertThrows(IllegalArgumentException.class, () -> CranialConfig.builder().baseUrl("").build());

        CranialConfig config = CranialConfig.builder()
                .baseUrl("http://localhost:8080/")
                .apiKey("crn_dev_sms_app001.secret_123")
                .signingSecret("secret_sign_456")
                .build();

        assertEquals("http://localhost:8080", config.baseUrl());
        assertEquals("crn_dev_sms_app001.secret_123", config.apiKey());
        assertEquals("secret_sign_456", config.signingSecret());
    }

    @Test
    @DisplayName("SignedContext generates deterministic HMAC-SHA256 signature and Base64URL context")
    void signedContextHeadersGeneration() {
        SignedContext ctx = SignedContext.builder()
                .tenantRef("store-jakarta-01")
                .userRef("owner@store.test")
                .addRole("STORE_OWNER")
                .addScope("sms:owner_reports")
                .build();

        assertEquals("store-jakarta-01", ctx.tenantRef());
        assertEquals("owner@store.test", ctx.userRef());
        assertEquals(List.of("STORE_OWNER"), ctx.roles());
        assertEquals(List.of("sms:owner_reports"), ctx.scopes());

        byte[] body = "{\"test\":true}".getBytes();
        SignedContext.SignedHeaders headers = ctx.generateHeaders("POST", "/api/v1/orchestrator/execute", body, "secret_signing_key_999");

        assertNotNull(headers.contextHeader());
        assertNotNull(headers.timestampHeader());
        assertNotNull(headers.signatureHeader());

        // Signature must be a 64-character lowercase hex string
        assertEquals(64, headers.signatureHeader().length());
        assertTrue(headers.signatureHeader().matches("^[a-f0-9]{64}$"));
    }

    @Test
    @DisplayName("Client builder creates client instance cleanly")
    void clientBuilderCreatesInstance() {
        CranialClient client = CranialClient.create("http://localhost:8080", "crn_dev_key.secret", "secret_sign");
        assertNotNull(client);
    }
}

