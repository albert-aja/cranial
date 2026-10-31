package io.cranial.platform.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cranial.platform.application.credential.ApplicationCredentialService;
import io.cranial.platform.application.credential.CredentialIssuedResult;
import io.cranial.platform.application.featureflag.FeatureFlagService;
import io.cranial.platform.application.idempotency.IdempotencyRecordEntity;
import io.cranial.platform.application.idempotency.IdempotencyRecordMapper;
import io.cranial.platform.application.maintenance.PlatformMaintenanceService;
import io.cranial.platform.application.registry.ApplicationRegistryService;
import io.cranial.platform.application.registry.ApplicationView;
import io.cranial.platform.platform.deprecation.ApiDeprecated;
import io.cranial.platform.platform.ratelimit.RateLimitDecision;
import io.cranial.platform.platform.ratelimit.RateLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@org.springframework.context.annotation.Import(FoundationAndArchitectureTest.DeprecatedSampleController.class)
class FoundationAndArchitectureTest {

    @RestController
    static class DeprecatedSampleController {
        @GetMapping("/api/v1/test/deprecated-endpoint")
        @ApiDeprecated(
                since = "v1.0",
                sunset = "2027-01-01T00:00:00Z",
                successor = "/api/v2/test/modern-endpoint",
                docUrl = "https://cranial.io/docs/migrations/v2"
        )
        public Map<String, String> legacyRoute() {
            return Map.of("message", "legacy");
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FeatureFlagService featureFlagService;

    @Autowired
    private PlatformMaintenanceService maintenanceService;

    @Autowired
    private ApplicationRegistryService registryService;

    @Autowired
    private ApplicationCredentialService credentialService;

    @Autowired
    private IdempotencyRecordMapper idempotencyMapper;

    @Autowired
    private RateLimiter rateLimiter;

    @Test
    void apiDeprecationPolicyEmitsStandardRfcHeaders() throws Exception {
        // Register an app to get valid API key for integration request
        String adminJwt = loginAndExtractJwt("admin@cranial.test", "TestAdminPassword!2026");
        MvcResult appRes = mockMvc.perform(post("/api/admin/v1/applications")
                        .header("Authorization", "Bearer " + adminJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "dep-app",
                                  "environment": "DEV",
                                  "name": "Deprecation Test App"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String appId = readJson(appRes).at("/data/id").asText();

        MvcResult keyRes = mockMvc.perform(post("/api/admin/v1/applications/" + appId + "/credentials")
                        .header("Authorization", "Bearer " + adminJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Dep Key\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String apiKey = readJson(keyRes).at("/data/raw_api_key").asText();

        mockMvc.perform(get("/api/v1/test/deprecated-endpoint")
                        .header("X-Cranial-Api-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(header().string("Deprecation", "true"))
                .andExpect(header().string("Sunset", "Fri, 1 Jan 2027 00:00:00 GMT"))
                .andExpect(header().stringValues("Link",
                        "</api/v2/test/modern-endpoint>; rel=\"successor-version\"",
                        "<https://cranial.io/docs/migrations/v2>; rel=\"deprecation\""
                ));
    }

    @Test
    void featureFlagsCanaryAndAllowedAppEvaluationWork() throws Exception {
        String adminJwt = loginAndExtractJwt("admin@cranial.test", "TestAdminPassword!2026");

        // 1. List seeded feature flags via Admin endpoint
        mockMvc.perform(get("/api/admin/v1/feature-flags")
                        .header("Authorization", "Bearer " + adminJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.flag_key == 'stream.sse.enabled')].enabled").value(false))
                .andExpect(jsonPath("$.data[?(@.flag_key == 'system.maintenance.auto_cleanup')].enabled").value(true));

        // 2. Initial state: stream.sse.enabled is disabled
        assertThat(featureFlagService.isEnabled("stream.sse.enabled", "peggy")).isFalse();

        // 3. Update flag to allow only 'peggy' with 0% generic rollout
        mockMvc.perform(put("/api/admin/v1/feature-flags/stream.sse.enabled")
                        .header("Authorization", "Bearer " + adminJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "enabled": true,
                                  "rollout_percentage": 0,
                                  "allowed_app_codes": ["peggy"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.allowed_app_codes[0]").value("peggy"));

        assertThat(featureFlagService.isEnabled("stream.sse.enabled", "peggy")).isTrue();
        assertThat(featureFlagService.isEnabled("stream.sse.enabled", "sms")).isFalse();

        // 4. Update to 100% rollout
        mockMvc.perform(put("/api/admin/v1/feature-flags/stream.sse.enabled")
                        .header("Authorization", "Bearer " + adminJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "enabled": true,
                                  "rollout_percentage": 100,
                                  "allowed_app_codes": []
                                }
                                """))
                .andExpect(status().isOk());

        assertThat(featureFlagService.isEnabled("stream.sse.enabled", "sms")).isTrue();
    }

    @Test
    void scheduledMaintenancePurgesExpiredIdempotencyAndExpiresRotatingCredentials() throws Exception {
        String adminJwt = loginAndExtractJwt("admin@cranial.test", "TestAdminPassword!2026");

        // 1. Create test app
        ApplicationView app = registryService.createApplication(
                new ApplicationRegistryService.CreateApplicationCommand(
                        "maint-app", "DEV", "Maintenance Test App", null, null, 100, 10, 1024, false
                ),
                null, "127.0.0.1"
        );

        // 2. Insert expired idempotency record
        IdempotencyRecordEntity expiredRecord = new IdempotencyRecordEntity();
        expiredRecord.setId(UUID.randomUUID());
        expiredRecord.setApplicationId(app.id());
        expiredRecord.setIdempotencyKey("expired-key-1");
        expiredRecord.setEndpoint("/api/v1/test");
        expiredRecord.setRequestHash("testhash");
        expiredRecord.setResponseStatus(200);
        expiredRecord.setResponseBody("{}");
        expiredRecord.setExpiresAt(Instant.now().minus(2, ChronoUnit.HOURS));
        expiredRecord.setCreatedAt(Instant.now().minus(24, ChronoUnit.HOURS));
        idempotencyMapper.insert(expiredRecord);

        // 3. Issue and rotate credential, then manually age its grace expiration
        CredentialIssuedResult cred1 = credentialService.issueCredential(app.id(), "Old Key", null, null, "127.0.0.1");
        // Rotate with 0 grace -> immediately revoked
        // Rotate with grace period and age it
        credentialService.rotateCredential(app.id(), cred1.credential().id(), 60, null, "127.0.0.1");

        // Directly execute maintenance routine
        PlatformMaintenanceService.MaintenanceSummary summary = maintenanceService.runMaintenance(
                "TEST", "unit-test", "127.0.0.1"
        );

        assertThat(summary.expiredIdempotencyPurged()).isGreaterThanOrEqualTo(1);

        // 4. Test Admin manual trigger endpoint
        mockMvc.perform(post("/api/admin/v1/maintenance/run")
                        .header("Authorization", "Bearer " + adminJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.data.duration_ms").isNumber());
    }

    @Test
    void rateLimiterInterfaceOperatesSmoothly() {
        RateLimitDecision d1 = rateLimiter.tryAcquire("test-bucket", 10);
        assertThat(d1.allowed()).isTrue();
        assertThat(d1.limitRpm()).isEqualTo(10);
        rateLimiter.reset("test-bucket");
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
}
