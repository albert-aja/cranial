package io.cranial.platform.analytics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cranial.platform.application.capability.CapabilityEntitlementService;
import io.cranial.platform.application.credential.ApplicationCredentialService;
import io.cranial.platform.application.credential.CredentialIssuedResult;
import io.cranial.platform.application.registry.ApplicationRegistryService;
import io.cranial.platform.application.registry.ApplicationView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DataIntelligenceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ApplicationRegistryService appService;

    @Autowired
    private ApplicationCredentialService credService;

    @Autowired
    private CapabilityEntitlementService entitlementService;

    private String adminToken;
    private ApplicationView entitledApp;
    private CredentialIssuedResult entitledCred;
    private ApplicationView unentitledApp;
    private CredentialIssuedResult unentitledCred;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = loginAndExtractJwt("admin@cranial.test", "TestAdminPassword!2026");

        String suffix = UUID.randomUUID().toString().substring(0, 8);
        entitledApp = appService.createApplication(
                new ApplicationRegistryService.CreateApplicationCommand(
                        "peggy-analytics-" + suffix,
                        "DEV",
                        "Peggy Analytics Client",
                        "Money management analytics and budget vs actual reporting",
                        List.of("http://localhost:3000"),
                        180,
                        10,
                        5 * 1024 * 1024,
                        false
                ),
                null,
                "127.0.0.1"
        );
        entitlementService.updateEntitlement(entitledApp.id(), "data_intelligence", true, null, null, "127.0.0.1");
        entitledCred = credService.issueCredential(
                entitledApp.id(),
                "Peggy Analytics Key",
                Instant.now().plus(Duration.ofDays(30)),
                null,
                "127.0.0.1"
        );

        unentitledApp = appService.createApplication(
                new ApplicationRegistryService.CreateApplicationCommand(
                        "unentitled-app-" + suffix,
                        "DEV",
                        "Unentitled Client",
                        "Client without data_intelligence capability",
                        List.of("http://localhost:3000"),
                        180,
                        10,
                        5 * 1024 * 1024,
                        false
                ),
                null,
                "127.0.0.1"
        );
        unentitledCred = credService.issueCredential(
                unentitledApp.id(),
                "Unentitled Key",
                Instant.now().plus(Duration.ofDays(30)),
                null,
                "127.0.0.1"
        );
    }

    private String loginAndExtractJwt(String email, String password) throws Exception {
        String resp = mockMvc.perform(post("/api/admin/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(resp);
        return node.path("data").path("access_token").asText();
    }

    @Test
    @DisplayName("Verify entitlement enforcement: unentitled app rejected with 403 Forbidden")
    void testEntitlementEnforcement() throws Exception {
        mockMvc.perform(post("/api/v1/analytics/query")
                        .header("X-Cranial-Api-Key", unentitledCred.rawApiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "dataset_code", "peggy_budget_monthly_2026"
                        ))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors[0].code").value("CAPABILITY_NOT_ENTITLED"));
    }

    @Test
    @DisplayName("Verify comprehensive Data Intelligence & Analytics capabilities (20.6)")
    void testDataIntelligenceCapabilities() throws Exception {
        // 1. List Datasets from Gateway & Admin
        mockMvc.perform(get("/api/v1/analytics/datasets")
                        .header("X-Cranial-Api-Key", entitledCred.rawApiKey()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[?(@.dataset_code == 'peggy_budget_monthly_2026')]").exists())
                .andExpect(jsonPath("$.data[?(@.dataset_code == 'peggy_cashflow_daily_q4')]").exists());

        mockMvc.perform(get("/api/admin/v1/analytics/datasets")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[?(@.dataset_code == 'peggy_budget_monthly_2026')]").exists());

        // 2. Deterministic Reporting Query with KPI & Provenance Verification
        String queryJson = mockMvc.perform(post("/api/v1/analytics/query")
                        .header("X-Cranial-Api-Key", entitledCred.rawApiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "dataset_code", "peggy_budget_monthly_2026",
                                "time_range", "ALL",
                                "bypass_cache", true,
                                "locale", "en"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dataset_code").value("peggy_budget_monthly_2026"))
                .andExpect(jsonPath("$.data.cached").value(false))
                .andExpect(jsonPath("$.data.kpis").isArray())
                .andExpect(jsonPath("$.data.kpis[?(@.name == 'total_budget')]").exists())
                .andExpect(jsonPath("$.data.kpis[?(@.name == 'total_actual')]").exists())
                .andExpect(jsonPath("$.data.kpis[?(@.name == 'net_variance')]").exists())
                .andExpect(jsonPath("$.data.kpis[?(@.name == 'savings_rate')]").exists())
                .andExpect(jsonPath("$.data.breakdown").isArray())
                .andExpect(jsonPath("$.data.provenance.engine").value("CRANIAL_DETERMINISTIC_STATISTICAL_ENGINE_V1"))
                .andReturn().getResponse().getContentAsString();

        JsonNode reportNode = objectMapper.readTree(queryJson).path("data");
        assertThat(reportNode.path("kpis").size()).isGreaterThanOrEqualTo(3);
        assertThat(reportNode.path("provenance").path("formula").asText()).contains("AGGREGATE_METRICS");

        // 3. Statistical Anomaly Detection (IQR Outlier Spike on 2026-10-15)
        String anomalyResp = mockMvc.perform(post("/api/v1/analytics/query")
                        .header("X-Cranial-Api-Key", entitledCred.rawApiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "dataset_code", "peggy_cashflow_daily_q4",
                                "detect_anomalies", true,
                                "bypass_cache", true,
                                "locale", "id"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.anomalies").isArray())
                .andExpect(jsonPath("$.data.anomalies[?(@.direction == 'SPIKE')]").exists())
                .andExpect(jsonPath("$.data.anomalies[?(@.severity == 'CRITICAL')]").exists())
                .andReturn().getResponse().getContentAsString();

        JsonNode anomalyReport = objectMapper.readTree(anomalyResp).path("data");
        JsonNode anomalies = anomalyReport.path("anomalies");
        JsonNode spikeNode = null;
        for (JsonNode node : anomalies) {
            if (node.path("actual_value").asDouble() > 4000000.0) {
                spikeNode = node;
                break;
            }
        }
        assertThat(spikeNode).isNotNull();
        String anomalyId = spikeNode.path("id").asText();
        assertThat(spikeNode.path("actual_value").asDouble()).isGreaterThan(4000000.0);

        // 4. Human Review Queue & False Positive Marking
        mockMvc.perform(get("/api/admin/v1/analytics/anomalies")
                        .param("status", "DETECTED")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == '" + anomalyId + "')]").exists());

        mockMvc.perform(post("/api/admin/v1/analytics/anomalies/" + anomalyId + "/review")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "status", "FALSE_POSITIVE",
                                "review_notes", "Verified authorized family celebration dinner"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FALSE_POSITIVE"))
                .andExpect(jsonPath("$.data.review_notes").value("Verified authorized family celebration dinner"));

        // 5. Natural Language Query Clarification on Vague Request
        mockMvc.perform(post("/api/v1/analytics/query")
                        .header("X-Cranial-Api-Key", entitledCred.rawApiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "natural_language_query", "ringkas data",
                                "locale", "id"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.clarification.needs_clarification").value(true))
                .andExpect(jsonPath("$.data.clarification.missing_fields").isArray())
                .andExpect(jsonPath("$.data.clarification.suggested_options").isNotEmpty());

        // 6. Natural Language Query with Inferred Intent
        mockMvc.perform(post("/api/v1/analytics/query")
                        .header("X-Cranial-Api-Key", entitledCred.rawApiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "natural_language_query", "Berapa pengeluaran dan anggaran Peggy bulan ini?",
                                "bypass_cache", true,
                                "locale", "id"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dataset_code").value("peggy_budget_monthly_2026"))
                .andExpect(jsonPath("$.data.natural_language_summary").isNotEmpty());

        // 7. Query Caching with SHA-256 Hash and Repeat Query Hit
        Map<String, Object> cacheQuery = Map.of(
                "dataset_code", "peggy_budget_monthly_2026",
                "time_range", "2026-10",
                "bypass_cache", false
        );

        // First call populates cache
        mockMvc.perform(post("/api/v1/analytics/query")
                        .header("X-Cranial-Api-Key", entitledCred.rawApiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cacheQuery)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cached").value(false));

        // Second identical call hits cache
        mockMvc.perform(post("/api/v1/analytics/query")
                        .header("X-Cranial-Api-Key", entitledCred.rawApiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cacheQuery)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cached").value(true));

        // Admin checks cache stats & purges cache
        mockMvc.perform(get("/api/admin/v1/analytics/cache/stats")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total_cached_queries").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

        mockMvc.perform(post("/api/admin/v1/analytics/cache/purge")
                        .param("dataset_code", "peggy_budget_monthly_2026")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.purged_entries").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

        // 8. Query Window Protection Guardrail (Rejects queries > max_query_window_days)
        mockMvc.perform(post("/api/v1/analytics/query")
                        .header("X-Cranial-Api-Key", entitledCred.rawApiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "dataset_code", "peggy_cashflow_daily_q4", // max window is 180 days
                                "start_date", "2024-01-01",
                                "end_date", "2026-10-31" // > 1000 days
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("VALIDATION_ERROR"));

        // 9. Execute Admin Analytics Evaluation Benchmark Suite -> 100% pass rate
        mockMvc.perform(post("/api/admin/v1/analytics/benchmarks/run")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total_cases").value(4))
                .andExpect(jsonPath("$.data.passed_cases").value(4))
                .andExpect(jsonPath("$.data.pass_rate").value(1.0))
                .andExpect(jsonPath("$.data.calculation_accuracy").value(1.0))
                .andExpect(jsonPath("$.data.anomaly_detection_rate").value(1.0));
    }
}

