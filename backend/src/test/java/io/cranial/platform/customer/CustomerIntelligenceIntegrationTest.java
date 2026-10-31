package io.cranial.platform.customer;

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
class CustomerIntelligenceIntegrationTest {

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
    private ApplicationView otherApp;
    private CredentialIssuedResult otherCred;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = loginAndExtractJwt("admin@cranial.test", "TestAdminPassword!2026");

        String suffix = UUID.randomUUID().toString().substring(0, 8);
        entitledApp = appService.createApplication(
                new ApplicationRegistryService.CreateApplicationCommand(
                        "peggy-customer-" + suffix,
                        "DEV",
                        "Peggy Customer Support Client",
                        "Customer service, sentiment analysis, and triage",
                        List.of("http://localhost:3000"),
                        180,
                        10,
                        5 * 1024 * 1024,
                        false
                ),
                null,
                "127.0.0.1"
        );
        entitlementService.updateEntitlement(entitledApp.id(), "customer_intelligence", true, null, null, "127.0.0.1");
        entitledCred = credService.issueCredential(
                entitledApp.id(),
                "Peggy Support Key",
                Instant.now().plus(Duration.ofDays(30)),
                null,
                "127.0.0.1"
        );

        otherApp = appService.createApplication(
                new ApplicationRegistryService.CreateApplicationCommand(
                        "sms-customer-" + suffix,
                        "DEV",
                        "SMS Support Client",
                        "SMS Store Management customer service",
                        List.of("http://localhost:3000"),
                        180,
                        10,
                        5 * 1024 * 1024,
                        false
                ),
                null,
                "127.0.0.1"
        );
        entitlementService.updateEntitlement(otherApp.id(), "customer_intelligence", true, null, null, "127.0.0.1");
        otherCred = credService.issueCredential(
                otherApp.id(),
                "SMS Support Key",
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
    @DisplayName("1. Sentiment Analysis with PII Redaction (Indonesian NIK, Email, Card numbers masked)")
    void testSentimentAnalysisWithPiiRedaction() throws Exception {
        String textWithPii = "Saya sangat puas dengan fitur pos dana darurat Peggy! Hubungi saya di albert@example.com atau 081234567890 NIK 3171012345670001.";

        mockMvc.perform(post("/api/v1/customer/sentiment/analyze")
                        .header("X-Cranial-Api-Key", entitledCred.rawApiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "text", textWithPii,
                                "locale", "id",
                                "redact_pii", true
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sentiment").value("POSITIVE"))
                .andExpect(jsonPath("$.data.pii_detected").value(true))
                .andExpect(jsonPath("$.data.redacted_text").isNotEmpty())
                .andExpect(jsonPath("$.data.score").isNumber());
    }

    @Test
    @DisplayName("2. Support Ticket Triage - Standard Budget Inquiry without escalation")
    void testSupportTicketTriageBudgetInquiry() throws Exception {
        String message = "Bagaimana cara mengatur pembagian pos anggaran 50-30-20 di aplikasi Peggy?";

        mockMvc.perform(post("/api/v1/customer/tickets/triage")
                        .header("X-Cranial-Api-Key", entitledCred.rawApiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "customer_name", "Budi Santoso",
                                "external_user_ref", "usr-8891",
                                "subject", "Tanya Alokasi Budget",
                                "message", message,
                                "locale", "id",
                                "source_channel", "IN_APP_CHAT"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.category").value("BUDGET_AND_POCKETS"))
                .andExpect(jsonPath("$.data.priority").value("NORMAL"))
                .andExpect(jsonPath("$.data.routing_queue").value("FINANCIAL_GUIDANCE"))
                .andExpect(jsonPath("$.data.escalation_recommended").value(false))
                .andExpect(jsonPath("$.data.promise_guard_flagged").value(false))
                .andExpect(jsonPath("$.data.suggested_canned_response").isNotEmpty());
    }

    @Test
    @DisplayName("3. Promise Guard - Blocks illegal banking refund and fund transfer commitments")
    void testPromiseGuardBlocksBankingCommitment() throws Exception {
        String message = "Janji sekarang juga uang saya akan dikembalikan 100% dan transfer saldo ke rekening BCA saya detik ini juga!";

        mockMvc.perform(post("/api/v1/customer/tickets/triage")
                        .header("X-Cranial-Api-Key", entitledCred.rawApiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "customer_name", "Rudi Angry",
                                "external_user_ref", "usr-9999",
                                "subject", "Tuntut Refund",
                                "message", message,
                                "locale", "id"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.category").value("PROMISE_POLICY_RESTRICTION"))
                .andExpect(jsonPath("$.data.priority").value("URGENT"))
                .andExpect(jsonPath("$.data.promise_guard_flagged").value(true))
                .andExpect(jsonPath("$.data.escalation_recommended").value(true))
                .andExpect(jsonPath("$.data.escalation_reason").value("PROMISE_POLICY_RESTRICTION"))
                .andExpect(jsonPath("$.data.routing_queue").value("HUMAN_DISPUTE_DESK"))
                .andExpect(jsonPath("$.data.suggested_canned_response").isNotEmpty());
    }

    @Test
    @DisplayName("4. Human Escalation Handoff & Admin Resolution Workflow")
    void testHumanEscalationHandoffAndResolution() throws Exception {
        // 1. Triage ticket requiring explicit human handoff
        String triageResp = mockMvc.perform(post("/api/v1/customer/tickets/triage")
                        .header("X-Cranial-Api-Key", entitledCred.rawApiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "customer_name", "Sarah Connor",
                                "external_user_ref", "usr-1002",
                                "subject", "Transfer to Agent",
                                "message", "Your bot is completely useless! Transfer me to a human manager immediately.",
                                "locale", "en"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.escalation_recommended").value(true))
                .andExpect(jsonPath("$.data.escalation_reason").value("USER_REQUESTED_AGENT"))
                .andReturn().getResponse().getContentAsString();

        JsonNode triageNode = objectMapper.readTree(triageResp);
        String ticketId = triageNode.path("data").path("ticket_id").asText();
        String escalationId = triageNode.path("data").path("escalation_package").path("escalation_id").asText();
        assertThat(escalationId).isNotBlank();

        // 2. Admin inspects escalations queue
        mockMvc.perform(get("/api/admin/v1/customer/escalations")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("status", "PENDING_HANDOFF"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());

        // 3. Admin resolves escalation
        mockMvc.perform(post("/api/admin/v1/customer/escalations/" + escalationId + "/resolve")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "resolution_notes", "Spoke with user, explained money tracking pocket setup."
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RESOLVED"));

        // 4. Verify ticket status is updated to RESOLVED
        mockMvc.perform(get("/api/admin/v1/customer/tickets/" + ticketId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RESOLVED"));
    }

    @Test
    @DisplayName("5. Customer Review Summarization with Evidence Quotes")
    void testReviewSummarization() throws Exception {
        List<String> reviews = List.of(
                "I love the 50-30-20 budget tracker! It helped me save $1,200 this quarter.",
                "Awesome clean interface and great expense categorization.",
                "The app is nice but please add automatic dark mode scheduling.",
                "Occasional freezing when scanning very long paper receipts."
        );

        mockMvc.perform(post("/api/v1/customer/reviews/summarize")
                        .header("X-Cranial-Api-Key", entitledCred.rawApiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "reviews", reviews,
                                "locale", "en"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total_analyzed").value(4))
                .andExpect(jsonPath("$.data.overall_sentiment").value("POSITIVE"))
                .andExpect(jsonPath("$.data.pros").isArray())
                .andExpect(jsonPath("$.data.evidence_quotes").isArray())
                .andExpect(jsonPath("$.data.executive_summary").isNotEmpty());
    }

    @Test
    @DisplayName("6. Product / Feature Discovery with Non-Banking Boundary Disclaimer")
    void testProductDiscoveryMoneyManagementBoundary() throws Exception {
        mockMvc.perform(post("/api/v1/customer/products/discover")
                        .header("X-Cranial-Api-Key", entitledCred.rawApiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "customer_goal", "I want to save for our upcoming wedding with my spouse while budgeting food",
                                "locale", "en"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recommended_features").isArray())
                .andExpect(jsonPath("$.data.money_management_boundary_disclaimer").isNotEmpty());
    }

    @Test
    @DisplayName("7. Customer Intelligence Benchmark Evaluation Suite Execution")
    void testCustomerIntelligenceBenchmarks() throws Exception {
        mockMvc.perform(post("/api/admin/v1/customer/benchmarks/run")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total_evaluated").isNumber())
                .andExpect(jsonPath("$.data.sentiment_accuracy_percent").isNumber())
                .andExpect(jsonPath("$.data.promise_guard_safety_rate_percent").value(100.0))
                .andExpect(jsonPath("$.data.classification_f1_score").isNumber())
                .andExpect(jsonPath("$.data.evaluation_details").isArray());
    }

    @Test
    @DisplayName("8. Strict Multi-Tenant and Cross-Application Isolation")
    void testTenantAndApplicationIsolation() throws Exception {
        // App A creates a ticket
        String triageResp = mockMvc.perform(post("/api/v1/customer/tickets/triage")
                        .header("X-Cranial-Api-Key", entitledCred.rawApiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "customer_name", "App A Customer",
                                "subject", "App A Private Inquiry",
                                "message", "How do I allocate savings in App A?"
                        ))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String ticketId = objectMapper.readTree(triageResp).path("data").path("ticket_id").asText();

        // App B attempts to read App A's ticket -> FORBIDDEN (403)
        mockMvc.perform(get("/api/v1/customer/tickets/" + ticketId)
                        .header("X-Cranial-Api-Key", otherCred.rawApiKey()))
                .andExpect(status().isForbidden());
    }
}

