package io.cranial.platform.orchestration;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.cranial.platform.application.capability.CapabilityEntitlementService;
import io.cranial.platform.application.credential.ApplicationCredentialService;
import io.cranial.platform.application.credential.CredentialIssuedResult;
import io.cranial.platform.application.registry.ApplicationRegistryService;
import io.cranial.platform.application.registry.ApplicationView;
import io.cranial.platform.orchestration.entity.SchemaEntity;
import io.cranial.platform.orchestration.entity.TaskDefinitionEntity;
import io.cranial.platform.orchestration.entity.ToolEntity;
import io.cranial.platform.orchestration.mapper.PromptMapper;
import io.cranial.platform.orchestration.mapper.SchemaMapper;
import io.cranial.platform.orchestration.mapper.TaskDefinitionMapper;
import io.cranial.platform.orchestration.mapper.ToolMapper;
import io.cranial.platform.orchestration.model.OrchestratorRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

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
class OrchestrationIntegrationTest {

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

    @Autowired
    private TaskDefinitionMapper taskMapper;

    @Autowired
    private PromptMapper promptMapper;

    @Autowired
    private SchemaMapper schemaMapper;

    @Autowired
    private ToolMapper toolMapper;

    private String adminToken;
    private ApplicationView app;
    private String rawApiKey;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = loginAndExtractJwt("admin@cranial.test", "TestAdminPassword!2026");

        String appCode = "test-orch-app-" + UUID.randomUUID().toString().substring(0, 8);
        app = appService.createApplication(
                new ApplicationRegistryService.CreateApplicationCommand(
                        appCode,
                        "DEV",
                        "Test Orchestrator Application",
                        "App for testing phase 3 orchestration and schema validation",
                        List.of("http://localhost:3000"),
                        60,
                        10,
                        1048576,
                        false
                ),
                null,
                "127.0.0.1"
        );

        // Entitle capabilities
        entitlementService.updateEntitlement(
                app.id(),
                "conversational_intelligence",
                true,
                null,
                null,
                "127.0.0.1"
        );

        CredentialIssuedResult cred = credService.issueCredential(
                app.id(),
                "Phase3 Integration Test Key",
                java.time.Instant.now().plus(java.time.Duration.ofDays(30)),
                null,
                "127.0.0.1"
        );
        rawApiKey = cred.rawApiKey();
    }

    private String loginAndExtractJwt(String email, String password) throws Exception {
        String resp = mockMvc.perform(post("/api/admin/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(resp).path("data").path("access_token").asText();
    }

    @Test
    @DisplayName("Admin can list and manage prompts with version rollback / publishing")
    void adminCanManagePrompts() throws Exception {
        // 1. List prompts
        mockMvc.perform(get("/api/admin/v1/prompts")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.data[?(@.prompt_code == 'financial_summary_prompt')]").exists());

        // 2. Create a v2 draft prompt
        Map<String, Object> saveReq = Map.of(
                "prompt_code", "financial_summary_prompt",
                "name", "Financial Summary Prompt v2",
                "description", "Updated v2 template",
                "capability_code", "conversational_intelligence",
                "task_code", "financial_summary",
                "version", 2,
                "status", "DRAFT",
                "system_template", "You are an expert money manager AI.",
                "user_template", "User: {{userId}}, Balance: {{balance}}, Summary: MOCK_JSON: {\"status\": \"COMPLETED\", \"summary\": \"Healthy\", \"recommended_action\": \"Save\"}",
                "input_variables_json", "[\"userId\", \"balance\"]"
        );

        String createResp = mockMvc.perform(post("/api/admin/v1/prompts")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(saveReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.data.version").value(2))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String v2PromptId = objectMapper.readTree(createResp).path("data").path("id").asText();

        // 3. Publish v2 -> publishes v2 and retires v1
        mockMvc.perform(post("/api/admin/v1/prompts/" + v2PromptId + "/publish")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));

        // 4. Verify version history shows both versions
        mockMvc.perform(get("/api/admin/v1/prompts/versions/financial_summary_prompt")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    @DisplayName("Admin can validate payload against registered JSON schema")
    void adminCanValidateSchemaPayload() throws Exception {
        SchemaEntity schema = schemaMapper.findActiveByCode("financial_summary_v1").orElseThrow();

        // Valid payload matching financial_summary_v1 schema
        Map<String, Object> validPayload = Map.of(
                "data", Map.of(
                        "summary", "Personal finances are stable with no overdraft.",
                        "key_takeaways", List.of("Emergency fund is intact", "Budget variance under 5%"),
                        "confidence_score", 0.95
                )
        );

        mockMvc.perform(post("/api/admin/v1/schemas/" + schema.getId() + "/validate")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validPayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.data.valid").value(true));

        // Invalid payload missing required "summary"
        Map<String, Object> invalidPayload = Map.of(
                "data", Map.of(
                        "key_takeaways", List.of("Some note")
                )
        );

        mockMvc.perform(post("/api/admin/v1/schemas/" + schema.getId() + "/validate")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidPayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.data.valid").value(false))
                .andExpect(jsonPath("$.data.errors").isArray());
    }

    @Test
    @DisplayName("Admin can test run registered tool in safe sandboxed environment")
    void adminCanTestRunTool() throws Exception {
        ToolEntity tool = toolMapper.findActiveByCode("peggy_get_account_balance").orElseThrow();

        Map<String, Object> testReq = Map.of(
                "tool_code", "peggy_get_account_balance",
                "parameters", Map.of("account_ref", "ACC-998877")
        );

        mockMvc.perform(post("/api/admin/v1/tools/" + tool.getId() + "/test")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.data.success").value(true))
                .andExpect(jsonPath("$.data.data.currency").value("IDR"))
                .andExpect(jsonPath("$.data.data.current_balance").exists());
    }

    @Test
    @DisplayName("Strict Money Management Boundary: banking and payment execution tools are forbidden")
    void paymentToolsAreStrictlyProhibited() throws Exception {
        UUID fakeToolId = UUID.randomUUID();
        ToolEntity paymentTool = new ToolEntity();
        paymentTool.setId(fakeToolId);
        paymentTool.setToolCode("peggy_execute_payment");
        paymentTool.setName("Execute Wire Payment");
        paymentTool.setDescription("Prohibited payment execution tool");
        paymentTool.setExecutionType("INTERNAL_MOCK");
        paymentTool.setIsReadOnly(false);
        paymentTool.setRiskLevel("HIGH");
        paymentTool.setStatus("ACTIVE");
        paymentTool.setTimeoutMs(5000);
        paymentTool.setCreatedAt(java.time.Instant.now());
        paymentTool.setUpdatedAt(java.time.Instant.now());
        toolMapper.insert(paymentTool);

        Map<String, Object> testReq = Map.of(
                "tool_code", "peggy_execute_payment",
                "parameters", Map.of("amount", 500)
        );

        // Must fail with 403 TOOL_PERMISSION_DENIED
        mockMvc.perform(post("/api/admin/v1/tools/" + fakeToolId + "/test")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testReq)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors[0].code").value("TOOL_PERMISSION_DENIED"));
    }

    @Test
    @DisplayName("Application executes orchestrated task with valid structured output")
    void applicationExecutesOrchestrationWithValidOutput() throws Exception {
        // Create custom task definition whose prompt outputs valid JSON
        UUID schemaId = schemaMapper.findActiveByCode("financial_summary_v1").orElseThrow().getId();

        UUID promptId = UUID.randomUUID();
        io.cranial.platform.orchestration.entity.PromptEntity customPrompt = new io.cranial.platform.orchestration.entity.PromptEntity();
        customPrompt.setId(promptId);
        customPrompt.setPromptCode("test_orch_valid_prompt");
        customPrompt.setName("Valid Output Prompt");
        customPrompt.setCapabilityCode("conversational_intelligence");
        customPrompt.setTaskCode("test_valid_summary");
        customPrompt.setVersion(1);
        customPrompt.setStatus("PUBLISHED");
        customPrompt.setSystemTemplate("Financial system instruction");
        customPrompt.setUserTemplate("Analyze account: {{accountId}}. Return MOCK_JSON: {\"summary\": \"Account healthy.\", \"key_takeaways\": [\"Save $100\", \"Maintain healthy ledger\"]}");
        customPrompt.setInputVariablesJson("[\"accountId\"]");
        customPrompt.setCreatedAt(java.time.Instant.now());
        customPrompt.setUpdatedAt(java.time.Instant.now());
        promptMapper.insert(customPrompt);

        UUID taskId = UUID.randomUUID();
        TaskDefinitionEntity customTask = new TaskDefinitionEntity();
        customTask.setId(taskId);
        customTask.setTaskCode("test_valid_summary");
        customTask.setName("Test Valid Summary Task");
        customTask.setCapabilityCode("conversational_intelligence");
        customTask.setDefaultPromptId(promptId);
        customTask.setOutputSchemaId(schemaId);
        customTask.setTimeoutSeconds(30);
        customTask.setAllowedDataSensitivity("INTERNAL");
        customTask.setIsActive(true);
        customTask.setCreatedAt(java.time.Instant.now());
        customTask.setUpdatedAt(java.time.Instant.now());
        taskMapper.insert(customTask);

        OrchestratorRequest req = new OrchestratorRequest(
                "test_valid_summary",
                "conversational_intelligence",
                Map.of("accountId", "ACC-1234"),
                "mock-chat-v1",
                "INTERNAL",
                false,
                false
        );

        mockMvc.perform(post("/api/v1/orchestrator/execute")
                        .header("X-Cranial-Api-Key", rawApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.data.task_code").value("test_valid_summary"))
                .andExpect(jsonPath("$.data.structured_output.summary").value("Account healthy."))
                .andExpect(jsonPath("$.data.structured_output.key_takeaways").isArray())
                .andExpect(jsonPath("$.data.steps").isArray());
    }

    @Test
    @DisplayName("Application orchestrated task fails validation if LLM produces invalid schema output")
    void applicationRejectsInvalidSchemaOutput() throws Exception {
        UUID schemaId = schemaMapper.findActiveByCode("financial_summary_v1").orElseThrow().getId();

        UUID promptId = UUID.randomUUID();
        io.cranial.platform.orchestration.entity.PromptEntity badPrompt = new io.cranial.platform.orchestration.entity.PromptEntity();
        badPrompt.setId(promptId);
        badPrompt.setPromptCode("test_orch_bad_prompt");
        badPrompt.setName("Bad Output Prompt");
        badPrompt.setCapabilityCode("conversational_intelligence");
        badPrompt.setTaskCode("test_invalid_summary");
        badPrompt.setVersion(1);
        badPrompt.setStatus("PUBLISHED");
        badPrompt.setSystemTemplate("Financial system instruction");
        // Outputs JSON missing "summary" and "key_takeaways"
        badPrompt.setUserTemplate("Analyze: {{accountId}}. Return MOCK_JSON: {\"partial_info\": \"incomplete\"}");
        badPrompt.setInputVariablesJson("[\"accountId\"]");
        badPrompt.setCreatedAt(java.time.Instant.now());
        badPrompt.setUpdatedAt(java.time.Instant.now());
        promptMapper.insert(badPrompt);

        UUID taskId = UUID.randomUUID();
        TaskDefinitionEntity badTask = new TaskDefinitionEntity();
        badTask.setId(taskId);
        badTask.setTaskCode("test_invalid_summary");
        badTask.setName("Test Bad Summary Task");
        badTask.setCapabilityCode("conversational_intelligence");
        badTask.setDefaultPromptId(promptId);
        badTask.setOutputSchemaId(schemaId);
        badTask.setTimeoutSeconds(30);
        badTask.setAllowedDataSensitivity("INTERNAL");
        badTask.setIsActive(true);
        badTask.setCreatedAt(java.time.Instant.now());
        badTask.setUpdatedAt(java.time.Instant.now());
        taskMapper.insert(badTask);

        OrchestratorRequest req = new OrchestratorRequest(
                "test_invalid_summary",
                "conversational_intelligence",
                Map.of("accountId", "ACC-9999"),
                "mock-chat-v1",
                "INTERNAL",
                false,
                false
        );

        mockMvc.perform(post("/api/v1/orchestrator/execute")
                        .header("X-Cranial-Api-Key", rawApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("SCHEMA_VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("Application orchestrated task invokes tool call and synthesizes response")
    void applicationExecutesToolFlow() throws Exception {
        UUID schemaId = schemaMapper.findActiveByCode("financial_summary_v1").orElseThrow().getId();

        UUID promptId = UUID.randomUUID();
        io.cranial.platform.orchestration.entity.PromptEntity toolPrompt = new io.cranial.platform.orchestration.entity.PromptEntity();
        toolPrompt.setId(promptId);
        toolPrompt.setPromptCode("test_orch_tool_prompt");
        toolPrompt.setName("Tool Flow Prompt");
        toolPrompt.setCapabilityCode("conversational_intelligence");
        toolPrompt.setTaskCode("test_tool_summary");
        toolPrompt.setVersion(1);
        toolPrompt.setStatus("PUBLISHED");
        toolPrompt.setSystemTemplate("Financial system instruction");
        // Emits tool call JSON block with account_ref
        toolPrompt.setUserTemplate("User: {{accountId}}. Return MOCK_JSON: {\"tool_call\": \"peggy_get_account_balance\", \"parameters\": {\"account_ref\": \"{{accountId}}\"}}");
        toolPrompt.setInputVariablesJson("[\"accountId\"]");
        toolPrompt.setCreatedAt(java.time.Instant.now());
        toolPrompt.setUpdatedAt(java.time.Instant.now());
        promptMapper.insert(toolPrompt);

        UUID taskId = UUID.randomUUID();
        TaskDefinitionEntity toolTask = new TaskDefinitionEntity();
        toolTask.setId(taskId);
        toolTask.setTaskCode("test_tool_summary");
        toolTask.setName("Test Tool Summary Task");
        toolTask.setCapabilityCode("conversational_intelligence");
        toolTask.setDefaultPromptId(promptId);
        toolTask.setOutputSchemaId(schemaId);
        toolTask.setTimeoutSeconds(30);
        toolTask.setAllowedDataSensitivity("INTERNAL");
        toolTask.setIsActive(true);
        toolTask.setCreatedAt(java.time.Instant.now());
        toolTask.setUpdatedAt(java.time.Instant.now());
        taskMapper.insert(toolTask);

        OrchestratorRequest req = new OrchestratorRequest(
                "test_tool_summary",
                "conversational_intelligence",
                Map.of("accountId", "ACC-TOOL-77"),
                "mock-chat-v1",
                "INTERNAL",
                true,
                false
        );

        mockMvc.perform(post("/api/v1/orchestrator/execute")
                        .header("X-Cranial-Api-Key", rawApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.data.tool_calls_count").value(1))
                .andExpect(jsonPath("$.data.structured_output.summary").exists())
                .andExpect(jsonPath("$.data.structured_output.key_takeaways").isArray());
    }

    @Test
    @DisplayName("Admin can inspect execution traces with step-by-step audit details")
    void adminCanInspectTraces() throws Exception {
        mockMvc.perform(get("/api/admin/v1/traces")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("Orchestrator resolves Indonesian prompt and executes with ID locale")
    void orchestratorExecutesWithIndonesianLocale() throws Exception {
        OrchestratorRequest req = new OrchestratorRequest(
                "financial_summary",
                "conversational_intelligence",
                Map.of(
                        "ledger_context", "Total pengeluaran bulan ini Rp 4.500.000",
                        "user_question", "Bagaimana pola pengeluaran saya?"
                ),
                "mock-chat-v1",
                "RESTRICTED",
                false,
                false,
                "id"
        );

        mockMvc.perform(post("/api/v1/orchestrator/execute")
                        .header("X-Cranial-Api-Key", rawApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.data.structured_output.summary").exists())
                .andExpect(jsonPath("$.data.structured_output.key_takeaways").isArray());
    }
}
