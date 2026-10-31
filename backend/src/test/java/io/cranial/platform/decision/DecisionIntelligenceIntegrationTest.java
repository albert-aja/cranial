package io.cranial.platform.decision;

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
class DecisionIntelligenceIntegrationTest {

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
                        "peggy-decision-" + suffix,
                        "DEV",
                        "Peggy Decision Client",
                        "Client entitled to decision_intelligence capability",
                        List.of("http://localhost:3000"),
                        180,
                        10,
                        5 * 1024 * 1024,
                        false
                ),
                null,
                "127.0.0.1"
        );
        entitlementService.updateEntitlement(
                entitledApp.id(),
                "decision_intelligence",
                true,
                null,
                null,
                "127.0.0.1"
        );
        entitledCred = credService.issueCredential(
                entitledApp.id(),
                "Peggy Decision Key",
                Instant.now().plus(Duration.ofDays(30)),
                null,
                "127.0.0.1"
        );

        unentitledApp = appService.createApplication(
                new ApplicationRegistryService.CreateApplicationCommand(
                        "unentitled-decision-" + suffix,
                        "DEV",
                        "Unentitled Decision Client",
                        "Client without decision_intelligence capability",
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
                "Unentitled Decision Key",
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
        mockMvc.perform(post("/api/v1/decision/recommendations")
                        .header("X-Cranial-Api-Key", unentitledCred.rawApiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "plan_code", "peggy_debt_snowball_vs_avalanche"
                        ))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors[0].code").value("CAPABILITY_NOT_ENTITLED"));
    }

    @Test
    @DisplayName("Verify comprehensive Decision Intelligence & Optimization capabilities (20.7)")
    void testDecisionIntelligenceCapabilities() throws Exception {
        // 1. List Plans via Gateway and Admin
        mockMvc.perform(get("/api/v1/decision/plans")
                        .header("X-Cranial-Api-Key", entitledCred.rawApiKey()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[?(@.plan_code == 'peggy_debt_snowball_vs_avalanche')]").exists())
                .andExpect(jsonPath("$.data[?(@.plan_code == 'peggy_savings_rebalance_optimizer')]").exists());

        mockMvc.perform(get("/api/admin/v1/decision/plans")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[?(@.plan_code == 'peggy_debt_snowball_vs_avalanche')]").exists());

        // 2. Generate Optimization Recommendation (Debt Payoff Strategy)
        String recResp = mockMvc.perform(post("/api/v1/decision/recommendations")
                        .header("X-Cranial-Api-Key", entitledCred.rawApiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "plan_code", "peggy_debt_snowball_vs_avalanche",
                                "simulation_mode", true,
                                "locale", "en"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recommendation_code").exists())
                .andExpect(jsonPath("$.data.status").value("PROPOSED"))
                .andExpect(jsonPath("$.data.simulation_mode").value(true))
                .andExpect(jsonPath("$.data.confidence_score").value(org.hamcrest.Matchers.greaterThanOrEqualTo(0.90)))
                .andExpect(jsonPath("$.data.abstained").value(false))
                .andExpect(jsonPath("$.data.trade_offs").isArray())
                .andExpect(jsonPath("$.data.scenario_diff").exists())
                .andExpect(jsonPath("$.data.constraints_validated").isArray())
                .andReturn().getResponse().getContentAsString();

        JsonNode recNode = objectMapper.readTree(recResp).path("data");
        String recId = recNode.path("id").asText();
        assertThat(recId).isNotBlank();

        // 3. Human Approval Workflow (Reviewer Approves Consequential Recommendation)
        mockMvc.perform(post("/api/admin/v1/decision/recommendations/" + recId + "/approve")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "decision", "APPROVED",
                                "review_notes", "Confirmed Debt Avalanche schedule with operator approval"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.reviewed_by").exists())
                .andExpect(jsonPath("$.data.review_notes").value("Confirmed Debt Avalanche schedule with operator approval"));

        // 4. Recommendation Outcome Feedback Tracking
        mockMvc.perform(post("/api/v1/decision/recommendations/" + recId + "/feedback")
                        .header("X-Cranial-Api-Key", entitledCred.rawApiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "rating", "HELPFUL",
                                "applied", true,
                                "measured_outcome", Map.of("interest_saved_idr", 2840000.0, "status", "ON_TRACK"),
                                "comments", "Applied extra Rp 1.5M payment to credit card; debt-free date on track"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rating").value("HELPFUL"))
                .andExpect(jsonPath("$.data.applied").value(true));

        mockMvc.perform(get("/api/admin/v1/decision/recommendations/" + recId + "/feedback")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].rating").value("HELPFUL"));

        // 5. Run Deterministic Optimization Solver Benchmarks (100% pass rate)
        mockMvc.perform(post("/api/admin/v1/decision/benchmarks/run")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.passed_scenarios").value(org.hamcrest.Matchers.greaterThanOrEqualTo(4)))
                .andExpect(jsonPath("$.data.constraint_satisfaction_rate").value(1.0))
                .andExpect(jsonPath("$.data.abstention_accuracy_rate").value(1.0));
    }
}

