package io.cranial.platform.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cranial.platform.admin.AdminAuthService;
import io.cranial.platform.platform.crypto.CryptoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class Phase1IntegrationAndSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AdminAuthService adminAuthService;

    @Autowired
    private CryptoService cryptoService;

    @Test
    void healthProbesOpenApiAndCorrelationIdWorkWithStrictActuatorProtection() throws Exception {
        mockMvc.perform(get("/actuator/health").header("X-Correlation-Id", "corr-health-001"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-Id", "corr-health-001"))
                .andExpect(jsonPath("$.status").value("UP"));

        mockMvc.perform(get("/v3/api-docs/integration-v1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("CRANIAL — Universal AI Integration Platform API"));

        mockMvc.perform(get("/v3/api-docs/admin-v1"))
                .andExpect(status().isOk());

        // Prometheus metrics must require operator authentication
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errors[0].code").value("UNAUTHORIZED"));
    }

    @Test
    void endToEndFoundationLifecycleRbacAntiSpoofingTenantIsolationIdempotencyAndLimits() throws Exception {
        // 1. Bad admin login audited & rejected
        mockMvc.perform(post("/api/admin/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@cranial.test\",\"password\":\"WrongPass!\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_CREDENTIALS"));

        // 2. Valid SUPER_ADMIN login
        String adminJwt = loginAndExtractJwt("admin@cranial.test", "TestAdminPassword!2026");

        // 3. Create AUDITOR operator and verify least-privilege RBAC enforcement
        adminAuthService.createOperatorIfNotExists(
                "auditor@cranial.test", "AuditorPass!2026", "ReadOnly Auditor", "AUDITOR"
        );
        String auditorJwt = loginAndExtractJwt("auditor@cranial.test", "AuditorPass!2026");

        mockMvc.perform(get("/api/admin/v1/applications")
                        .header("Authorization", "Bearer " + auditorJwt))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/v1/applications")
                        .header("Authorization", "Bearer " + auditorJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"unauth-app\",\"environment\":\"DEV\",\"name\":\"Blocked\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors[0].code").value("FORBIDDEN"));

        mockMvc.perform(get("/api/admin/v1/diagnostics")
                        .header("Authorization", "Bearer " + auditorJwt))
                .andExpect(status().isForbidden());

        // 4. Register Peggy (money management, non-banking) and SMS in PROD; verify local-first defaults
        MvcResult peggyRes = mockMvc.perform(post("/api/admin/v1/applications")
                        .header("Authorization", "Bearer " + adminJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "peggy",
                                  "environment": "PROD",
                                  "name": "Peggy Money Management",
                                  "description": "Personal & collaborative money management (non-banking)",
                                  "rate_limit_rpm": 5,
                                  "max_input_bytes": 2048
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.code").value("peggy"))
                .andExpect(jsonPath("$.data.settings.allow_hosted_providers").value(false))
                .andExpect(jsonPath("$.data.settings.allow_sensitive_external").value(false))
                .andReturn();
        String peggyId = readJson(peggyRes).at("/data/id").asText();

        MvcResult smsRes = mockMvc.perform(post("/api/admin/v1/applications")
                        .header("Authorization", "Bearer " + adminJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "sms",
                                  "environment": "PROD",
                                  "name": "Store Management System",
                                  "rate_limit_rpm": 60,
                                  "max_input_bytes": 4096
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String smsId = readJson(smsRes).at("/data/id").asText();

        // Reject enabling sensitive external sharing while hosted providers disabled
        mockMvc.perform(put("/api/admin/v1/applications/" + peggyId + "/settings")
                        .header("Authorization", "Bearer " + adminJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"allow_sensitive_external\": true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("VALIDATION_ERROR"));

        // 5. Issue credential (reveal-once), rotate with grace window, and revoke old key
        MvcResult key1Res = mockMvc.perform(post("/api/admin/v1/applications/" + peggyId + "/credentials")
                        .header("Authorization", "Bearer " + adminJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Peggy Primary Key\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.raw_api_key").isNotEmpty())
                .andExpect(jsonPath("$.data.raw_signing_secret").isNotEmpty())
                .andReturn();
        JsonNode key1Json = readJson(key1Res).at("/data");
        String cred1Id = key1Json.get("credential").get("id").asText();
        String rawApiKey1 = key1Json.get("raw_api_key").asText();

        // Listing credentials must NEVER leak raw_api_key or raw_signing_secret
        MvcResult listCredsRes = mockMvc.perform(get("/api/admin/v1/applications/" + peggyId + "/credentials")
                        .header("Authorization", "Bearer " + adminJwt))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(listCredsRes.getResponse().getContentAsString())
                .doesNotContain(rawApiKey1)
                .doesNotContain("raw_signing_secret");

        // Rotate credential (grace 30m) -> both keys valid until cred1 explicitly revoked
        MvcResult key2Res = mockMvc.perform(post("/api/admin/v1/applications/" + peggyId + "/credentials/" + cred1Id + "/rotate")
                        .header("Authorization", "Bearer " + adminJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"grace_period_minutes\": 30}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode key2Json = readJson(key2Res).at("/data");
        String rawApiKey2 = key2Json.get("raw_api_key").asText();
        String signingSecret2 = key2Json.get("raw_signing_secret").asText();

        mockMvc.perform(get("/api/v1/gateway/context").header("X-Cranial-Api-Key", rawApiKey1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.application_code").value("peggy"));

        mockMvc.perform(post("/api/admin/v1/applications/" + peggyId + "/credentials/" + cred1Id + "/revoke")
                        .header("Authorization", "Bearer " + adminJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REVOKED"));

        mockMvc.perform(get("/api/v1/gateway/context").header("X-Cranial-Api-Key", rawApiKey1))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errors[0].code").value("CREDENTIAL_REVOKED"));

        // Reset Peggy rate limit to generous limit for functional checks before testing rate limit exhaustion at end
        mockMvc.perform(put("/api/admin/v1/applications/" + peggyId + "/settings")
                        .header("Authorization", "Bearer " + adminJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rate_limit_rpm\": 100}"))
                .andExpect(status().isOk());

        // 6. Anti-spoofing: Peggy cannot claim application_id = "sms"
        mockMvc.perform(post("/api/v1/gateway/verify-request")
                        .header("X-Cranial-Api-Key", rawApiKey2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "request_id": "req-spoof-1",
                                  "application_id": "sms",
                                  "capability": "document_intelligence",
                                  "task": "receipt_extraction",
                                  "schema_version": "1.0"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors[0].code").value("APP_IDENTITY_MISMATCH"));

        // Anti-spoofing: Unverified identity claims in JSON body context rejected when unsigned
        mockMvc.perform(post("/api/v1/gateway/verify-request")
                        .header("X-Cranial-Api-Key", rawApiKey2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "request_id": "req-spoof-2",
                                  "application_id": "peggy",
                                  "capability": "document_intelligence",
                                  "task": "receipt_extraction",
                                  "schema_version": "1.0",
                                  "context": { "tenant_id": "forged-tenant", "roles": ["OWNER"] }
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_CONTEXT_SIGNATURE"));

        // 7. Capability Entitlement Gate: Denied before enabled, allowed after enabled
        String validEnvelopeBody = """
                {
                  "request_id": "req-ocr-001",
                  "application_id": "peggy",
                  "capability": "document_intelligence",
                  "task": "receipt_extraction",
                  "schema_version": "1.0",
                  "input": { "document_id": "doc-123" },
                  "context": { "locale": "id-ID" }
                }
                """;

        mockMvc.perform(post("/api/v1/gateway/verify-request")
                        .header("X-Cranial-Api-Key", rawApiKey2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validEnvelopeBody))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors[0].code").value("CAPABILITY_NOT_ENTITLED"));

        mockMvc.perform(put("/api/admin/v1/applications/" + peggyId + "/capabilities/document_intelligence")
                        .header("Authorization", "Bearer " + adminJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\": true, \"quota_daily_requests\": 500}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(true));

        // 8. Provision tenants & memberships; verify HMAC context signing + cross-tenant isolation
        MvcResult peggyTenantRes = mockMvc.perform(post("/api/admin/v1/tenants")
                        .header("Authorization", "Bearer " + adminJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "application_id", peggyId,
                                "external_tenant_ref", "pocket-shared-01",
                                "name", "Family Shared Pocket"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        String peggyTenantId = readJson(peggyTenantRes).at("/data/id").asText();

        mockMvc.perform(put("/api/admin/v1/tenants/" + peggyTenantId + "/memberships")
                        .header("Authorization", "Bearer " + adminJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"external_user_ref\":\"user-albert\",\"member_role\":\"MEMBER\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/v1/tenants")
                        .header("Authorization", "Bearer " + adminJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "application_id", smsId,
                                "external_tenant_ref", "sms-store-jakarta",
                                "name", "SMS Jakarta Branch"
                        ))))
                .andExpect(status().isCreated());

        // Valid signed context for Peggy's own tenant + active member succeeds + Idempotency-Key works
        SignedHeaders validSigned = signContext(
                signingSecret2, "POST", "/api/v1/gateway/verify-request", validEnvelopeBody,
                "{\"tenant_ref\":\"pocket-shared-01\",\"user_ref\":\"user-albert\",\"roles\":[\"MEMBER\"]}",
                Instant.now().getEpochSecond()
        );

        mockMvc.perform(post("/api/v1/gateway/verify-request")
                        .header("X-Cranial-Api-Key", rawApiKey2)
                        .header("Idempotency-Key", "idem-001")
                        .header(SignedContextVerifier.HEADER_CONTEXT, validSigned.context())
                        .header(SignedContextVerifier.HEADER_TIMESTAMP, validSigned.timestamp())
                        .header(SignedContextVerifier.HEADER_SIGNATURE, validSigned.signature())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validEnvelopeBody))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Idempotent-Replay", "false"))
                .andExpect(jsonPath("$.status").value("completed"))
                .andExpect(jsonPath("$.data.verified_tenant_ref").value("pocket-shared-01"))
                .andExpect(jsonPath("$.data.verified_user_ref").value("user-albert"));

        // Replay exact same Idempotency-Key + payload -> replayed = true
        mockMvc.perform(post("/api/v1/gateway/verify-request")
                        .header("X-Cranial-Api-Key", rawApiKey2)
                        .header("Idempotency-Key", "idem-001")
                        .header(SignedContextVerifier.HEADER_CONTEXT, validSigned.context())
                        .header(SignedContextVerifier.HEADER_TIMESTAMP, validSigned.timestamp())
                        .header(SignedContextVerifier.HEADER_SIGNATURE, validSigned.signature())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validEnvelopeBody))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Idempotent-Replay", "true"));

        // Reuse Idempotency-Key with altered payload -> 409 IDEMPOTENCY_KEY_CONFLICT
        String alteredBody = validEnvelopeBody.replace("doc-123", "doc-999");
        SignedHeaders alteredSigned = signContext(
                signingSecret2, "POST", "/api/v1/gateway/verify-request", alteredBody,
                "{\"tenant_ref\":\"pocket-shared-01\",\"user_ref\":\"user-albert\",\"roles\":[\"MEMBER\"]}",
                Instant.now().getEpochSecond()
        );
        mockMvc.perform(post("/api/v1/gateway/verify-request")
                        .header("X-Cranial-Api-Key", rawApiKey2)
                        .header("Idempotency-Key", "idem-001")
                        .header(SignedContextVerifier.HEADER_CONTEXT, alteredSigned.context())
                        .header(SignedContextVerifier.HEADER_TIMESTAMP, alteredSigned.timestamp())
                        .header(SignedContextVerifier.HEADER_SIGNATURE, alteredSigned.signature())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(alteredBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("IDEMPOTENCY_KEY_CONFLICT"));

        // Cross-application tenant access attempt (Peggy trying to access SMS tenant) -> 403 TENANT_NOT_AUTHORIZED
        SignedHeaders crossTenant = signContext(
                signingSecret2, "GET", "/api/v1/gateway/context", "",
                "{\"tenant_ref\":\"sms-store-jakarta\",\"user_ref\":\"user-albert\"}",
                Instant.now().getEpochSecond()
        );
        mockMvc.perform(get("/api/v1/gateway/context")
                        .header("X-Cranial-Api-Key", rawApiKey2)
                        .header(SignedContextVerifier.HEADER_CONTEXT, crossTenant.context())
                        .header(SignedContextVerifier.HEADER_TIMESTAMP, crossTenant.timestamp())
                        .header(SignedContextVerifier.HEADER_SIGNATURE, crossTenant.signature()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors[0].code").value("TENANT_NOT_AUTHORIZED"));

        // Non-member user trying to access Peggy shared pocket -> 403 TENANT_NOT_AUTHORIZED
        SignedHeaders nonMember = signContext(
                signingSecret2, "GET", "/api/v1/gateway/context", "",
                "{\"tenant_ref\":\"pocket-shared-01\",\"user_ref\":\"intruder-user\"}",
                Instant.now().getEpochSecond()
        );
        mockMvc.perform(get("/api/v1/gateway/context")
                        .header("X-Cranial-Api-Key", rawApiKey2)
                        .header(SignedContextVerifier.HEADER_CONTEXT, nonMember.context())
                        .header(SignedContextVerifier.HEADER_TIMESTAMP, nonMember.timestamp())
                        .header(SignedContextVerifier.HEADER_SIGNATURE, nonMember.signature()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors[0].code").value("TENANT_NOT_AUTHORIZED"));

        // Expired timestamp replay rejected
        SignedHeaders replayExpired = signContext(
                signingSecret2, "GET", "/api/v1/gateway/context", "",
                "{\"tenant_ref\":\"pocket-shared-01\",\"user_ref\":\"user-albert\"}",
                Instant.now().minusSeconds(900).getEpochSecond()
        );
        mockMvc.perform(get("/api/v1/gateway/context")
                        .header("X-Cranial-Api-Key", rawApiKey2)
                        .header(SignedContextVerifier.HEADER_CONTEXT, replayExpired.context())
                        .header(SignedContextVerifier.HEADER_TIMESTAMP, replayExpired.timestamp())
                        .header(SignedContextVerifier.HEADER_SIGNATURE, replayExpired.signature()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errors[0].code").value("CONTEXT_TIMESTAMP_EXPIRED"));

        // 9. Max payload size enforcement (Peggy configured at 2048 bytes)
        String oversizedBody = "{\"application_id\":\"peggy\",\"capability\":\"document_intelligence\",\"task\":\"t\",\"schema_version\":\"1.0\",\"input\":{\"blob\":\""
                + "A".repeat(3000) + "\"}}";
        mockMvc.perform(post("/api/v1/gateway/verify-request")
                        .header("X-Cranial-Api-Key", rawApiKey2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(oversizedBody))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.errors[0].code").value("PAYLOAD_TOO_LARGE"));

        // 10. Suspension blocks application immediately
        mockMvc.perform(patch("/api/admin/v1/applications/" + peggyId + "/status")
                        .header("Authorization", "Bearer " + adminJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUSPENDED\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/gateway/context").header("X-Cranial-Api-Key", rawApiKey2))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors[0].code").value("APP_SUSPENDED"));

        // 11. Overview & Audit logs verify recorded events without leaking secrets
        mockMvc.perform(get("/api/admin/v1/overview")
                        .header("Authorization", "Bearer " + adminJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.platform").value("CRANIAL"))
                .andExpect(jsonPath("$.data.governance.automatic_paid_fallback").value(false));

        MvcResult auditRes = mockMvc.perform(get("/api/admin/v1/audit-logs?outcome=DENIED")
                        .header("Authorization", "Bearer " + auditorJwt))
                .andExpect(status().isOk())
                .andReturn();
        String auditPayload = auditRes.getResponse().getContentAsString();
        assertThat(auditPayload)
                .contains("CAPABILITY_ACCESS_DENIED")
                .contains("TENANT_ISOLATION_DENIED")
                .doesNotContain(rawApiKey2)
                .doesNotContain(signingSecret2);
    }

    @Test
    void rateLimiterRejectsExcessRequestsWith429AndRetryAfterHeader() throws Exception {
        String adminJwt = loginAndExtractJwt("admin@cranial.test", "TestAdminPassword!2026");

        MvcResult appRes = mockMvc.perform(post("/api/admin/v1/applications")
                        .header("Authorization", "Bearer " + adminJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "worker-app",
                                  "environment": "DEV",
                                  "name": "Worker App",
                                  "rate_limit_rpm": 2
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String appId = readJson(appRes).at("/data/id").asText();

        MvcResult keyRes = mockMvc.perform(post("/api/admin/v1/applications/" + appId + "/credentials")
                        .header("Authorization", "Bearer " + adminJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Worker Key\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String apiKey = readJson(keyRes).at("/data/raw_api_key").asText();

        mockMvc.perform(get("/api/v1/gateway/context").header("X-Cranial-Api-Key", apiKey))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/gateway/context").header("X-Cranial-Api-Key", apiKey))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/gateway/context").header("X-Cranial-Api-Key", apiKey))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.errors[0].code").value("RATE_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.errors[0].retryable").value(true));
    }

    private String loginAndExtractJwt(String email, String password) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/admin/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return readJson(res).at("/data/access_token").asText();
    }

    private JsonNode readJson(MvcResult res) throws Exception {
        return objectMapper.readTree(res.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private record SignedHeaders(String context, String timestamp, String signature) {
    }

    private SignedHeaders signContext(
            String signingSecret,
            String method,
            String path,
            String rawBody,
            String contextJson,
            long epochSeconds
    ) {
        String encodedContext = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(contextJson.getBytes(StandardCharsets.UTF_8));
        String ts = String.valueOf(epochSeconds);
        String bodySha = cryptoService.sha256Hex(rawBody.getBytes(StandardCharsets.UTF_8));
        String canonical = ts + "." + method.toUpperCase() + "." + path + "." + encodedContext + "." + bodySha;
        String sig = cryptoService.hmacSha256Hex(signingSecret, canonical);
        return new SignedHeaders(encodedContext, ts, sig);
    }
}
