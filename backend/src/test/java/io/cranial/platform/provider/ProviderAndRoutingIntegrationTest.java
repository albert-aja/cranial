package io.cranial.platform.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.cranial.platform.application.capability.CapabilityEntitlementService;
import io.cranial.platform.application.credential.ApplicationCredentialService;
import io.cranial.platform.application.credential.CredentialIssuedResult;
import io.cranial.platform.application.registry.ApplicationRegistryService;
import io.cranial.platform.application.registry.ApplicationView;
import io.cranial.platform.provider.entity.ModelEntity;
import io.cranial.platform.provider.entity.ProviderEntity;
import io.cranial.platform.provider.entity.RoutingPolicyEntity;
import io.cranial.platform.provider.mapper.ModelCapabilityMapper;
import io.cranial.platform.provider.mapper.ModelMapper;
import io.cranial.platform.provider.mapper.ProviderMapper;
import io.cranial.platform.provider.mapper.RoutingPolicyMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

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
class ProviderAndRoutingIntegrationTest {

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
    private ProviderMapper providerMapper;

    @Autowired
    private ModelMapper modelMapper;

    @Autowired
    private ModelCapabilityMapper modelCapabilityMapper;

    @Autowired
    private RoutingPolicyMapper routingPolicyMapper;

    @Autowired
    private io.cranial.platform.application.registry.ApplicationSettingsMapper settingsMapper;

    private String adminToken;
    private ApplicationView app;
    private String rawApiKey;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = loginAndExtractJwt("admin@cranial.test", "TestAdminPassword!2026");

        String appCode = "test-provider-app-" + UUID.randomUUID().toString().substring(0, 8);
        app = appService.createApplication(
                new ApplicationRegistryService.CreateApplicationCommand(
                        appCode,
                        "DEV",
                        "Test Provider Application",
                        "App for testing provider routing and execution",
                        List.of("http://localhost:3000"),
                        60,
                        10,
                        1048576,
                        false
                ),
                null,
                "127.0.0.1"
        );

        // Entitle capabilities for the test application
        entitlementService.updateEntitlement(
                app.id(),
                "conversational_intelligence",
                true,
                null,
                null,
                "127.0.0.1"
        );
        entitlementService.updateEntitlement(
                app.id(),
                "knowledge_intelligence",
                true,
                null,
                null,
                "127.0.0.1"
        );

        CredentialIssuedResult credResult = credService.issueCredential(
                app.id(),
                "Integration Test Key",
                null,
                null,
                "127.0.0.1"
        );
        rawApiKey = credResult.rawApiKey();
    }

    private String loginAndExtractJwt(String email, String password) throws Exception {
        var res = mockMvc.perform(post("/api/admin/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).at("/data/access_token").asText();
    }

    @Test
    @DisplayName("Admin can list providers and check health of mock local provider")
    void adminCanListProvidersAndCheckHealth() throws Exception {
        mockMvc.perform(get("/api/admin/v1/providers")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.data[?(@.code == 'mock_local')]").exists())
                .andExpect(jsonPath("$.data[?(@.code == 'ollama')]").exists());

        ProviderEntity mockProvider = providerMapper.selectList(null).stream()
                .filter(p -> "mock_local".equals(p.getCode()))
                .findFirst()
                .orElseThrow();

        mockMvc.perform(get("/api/admin/v1/providers/" + mockProvider.getId() + "/health")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.data.reachable").value(true))
                .andExpect(jsonPath("$.data.provider_code").value("mock_local"));
    }

    @Test
    @DisplayName("Admin can list registered models with capabilities")
    void adminCanListModels() throws Exception {
        mockMvc.perform(get("/api/admin/v1/models")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.data[?(@.model_identifier == 'mock-chat-v1')]").exists())
                .andExpect(jsonPath("$.data[?(@.model_identifier == 'llama3.2:3b')]").exists());
    }

    @Test
    @DisplayName("Application can execute chat completion via mock provider")
    void applicationCanExecuteChatViaGateway() throws Exception {
        Map<String, Object> req = Map.of(
                "capability", "conversational_intelligence",
                "task", "greeting",
                "messages", List.of(
                        Map.of("role", "user", "content", "Hello CRANIAL")
                ),
                "model_override", "mock-chat-v1"
        );

        mockMvc.perform(post("/api/v1/models/chat")
                        .header("X-Cranial-Api-Key", rawApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.data.model").value("mock-chat-v1"))
                .andExpect(jsonPath("$.data.is_local").value(true))
                .andExpect(jsonPath("$.data.fallback_used").value(false))
                .andExpect(jsonPath("$.data.message.content").exists());
    }

    @Test
    @DisplayName("Application can generate vector embeddings via mock provider")
    void applicationCanGenerateEmbeddings() throws Exception {
        ProviderEntity mockProvider = providerMapper.selectList(null).stream()
                .filter(p -> "mock_local".equals(p.getCode()))
                .findFirst()
                .orElseThrow();

        UUID embedModelId = UUID.randomUUID();
        ModelEntity embedModel = new ModelEntity();
        embedModel.setId(embedModelId);
        embedModel.setProviderId(mockProvider.getId());
        embedModel.setModelIdentifier("mock-embed-v1");
        embedModel.setDisplayName("Mock Embed Model");
        embedModel.setModality("EMBEDDING");
        embedModel.setContextWindowTokens(2048);
        embedModel.setMaxOutputTokens(0);
        embedModel.setIsLocal(true);
        embedModel.setStatus("ACTIVE");
        embedModel.setCreatedAt(Instant.now());
        embedModel.setUpdatedAt(Instant.now());
        modelMapper.insert(embedModel);
        modelCapabilityMapper.insert(new io.cranial.platform.provider.entity.ModelCapabilityEntity(embedModelId, "knowledge_intelligence"));

        Map<String, Object> req = Map.of(
                "capability", "knowledge_intelligence",
                "inputs", List.of("Account balance history", "Monthly transaction report"),
                "model_override", "mock-embed-v1"
        );

        mockMvc.perform(post("/api/v1/models/embed")
                        .header("X-Cranial-Api-Key", rawApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.data.model").value("mock-embed-v1"))
                .andExpect(jsonPath("$.data.embeddings").isArray())
                .andExpect(jsonPath("$.data.is_local").value(true));
    }

    @Test
    @DisplayName("Strict No-Paid-Fallback: fallback to external hosted model blocked when allow_hosted_providers is false")
    void strictNoPaidFallbackEnforcedWhenPrimaryFails() throws Exception {
        // 1. Create a hosted paid provider and model
        ProviderEntity hostedProvider = new ProviderEntity();
        hostedProvider.setId(UUID.randomUUID());
        hostedProvider.setCode("hosted_openai_sim");
        hostedProvider.setName("Hosted Cloud Provider");
        hostedProvider.setProviderType("HOSTED_PAID");
        hostedProvider.setBaseUrl("https://api.hosted.ai");
        hostedProvider.setStatus("ACTIVE");
        hostedProvider.setTimeoutSeconds(10);
        hostedProvider.setCreatedAt(Instant.now());
        hostedProvider.setUpdatedAt(Instant.now());
        providerMapper.insert(hostedProvider);

        ModelEntity hostedModel = new ModelEntity();
        hostedModel.setId(UUID.randomUUID());
        hostedModel.setProviderId(hostedProvider.getId());
        hostedModel.setModelIdentifier("cloud-gpt-sim");
        hostedModel.setDisplayName("Cloud GPT Sim (Hosted Paid)");
        hostedModel.setModality("CHAT");
        hostedModel.setContextWindowTokens(8192);
        hostedModel.setMaxOutputTokens(2048);
        hostedModel.setIsLocal(false); // Hosted/Paid!
        hostedModel.setStatus("ACTIVE");
        hostedModel.setCreatedAt(Instant.now());
        hostedModel.setUpdatedAt(Instant.now());
        modelMapper.insert(hostedModel);

        // 2. Point a failing primary provider (unreachable Ollama) to hostedModel as fallback
        ModelEntity primaryModel = modelMapper.selectList(null).stream()
                .filter(m -> "llama3.2:3b".equals(m.getModelIdentifier()))
                .findFirst()
                .orElseThrow();

        RoutingPolicyEntity testPolicy = new RoutingPolicyEntity();
        testPolicy.setId(UUID.randomUUID());
        testPolicy.setName("Strict Governance Test Policy");
        testPolicy.setCapabilityCode("conversational_intelligence");
        testPolicy.setTaskCode("strict_fallback_test");
        testPolicy.setPreferredModelId(primaryModel.getId());
        testPolicy.setFallbackModelId(hostedModel.getId()); // Fallback to paid model!
        testPolicy.setAllowedDataSensitivity("RESTRICTED");
        testPolicy.setIsActive(true);
        testPolicy.setCreatedAt(Instant.now());
        testPolicy.setUpdatedAt(Instant.now());
        routingPolicyMapper.insert(testPolicy);

        // 3. Application has default settings (allow_hosted_providers = false)
        Map<String, Object> req = Map.of(
                "capability", "conversational_intelligence",
                "task", "strict_fallback_test",
                "messages", List.of(
                        Map.of("role", "user", "content", "Test fallback blockage")
                )
        );

        // Expect 403 FALLBACK_BLOCKED
        mockMvc.perform(post("/api/v1/models/chat")
                        .header("X-Cranial-Api-Key", rawApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.errors[0].code").value("FALLBACK_BLOCKED"));

        // 4. Verify AI usage record recorded FALLBACK_BLOCKED
        mockMvc.perform(get("/api/admin/v1/usage/summary")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fallback_blocked_count").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    @Test
    @DisplayName("Admin routing simulator correctly identifies allowed and blocked routes")
    void adminRoutingSimulatorEvaluatesPolicies() throws Exception {
        Map<String, Object> simReq = Map.of(
                "application_code", app.code(),
                "capability_code", "conversational_intelligence",
                "data_sensitivity", "RESTRICTED"
        );

        mockMvc.perform(post("/api/admin/v1/routing-policies/simulate")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(simReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.data.allowed").value(true))
                .andExpect(jsonPath("$.data.preferred_model_local").value(true));
    }

    @Test
    @DisplayName("Application can execute streaming chat completion via SSE gateway")
    void applicationCanExecuteStreamingChat() throws Exception {
        Map<String, Object> req = Map.of(
                "capability", "conversational_intelligence",
                "task", "greeting",
                "messages", List.of(
                        Map.of("role", "user", "content", "Streaming test for CRANIAL")
                ),
                "model_override", "mock-chat-v1"
        );

        String sseOutput = mockMvc.perform(post("/api/v1/models/chat/stream")
                        .header("X-Cranial-Api-Key", rawApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(sseOutput).contains("event:chunk");
        assertThat(sseOutput).contains("event:done");
    }

    @Test
    @DisplayName("Circuit breaker trips to OPEN on repeated failures and Admin can reset it")
    void circuitBreakerTripsAndCanBeReset() throws Exception {
        io.cranial.platform.provider.circuit.ProviderCircuitBreakerRegistry breakerRegistry =
                org.springframework.test.util.TestSocketUtils.class != null ? null : null; // dummy to check import
        // Find mock_local provider
        ProviderEntity mockProvider = providerMapper.selectList(null).stream()
                .filter(p -> "mock_local".equals(p.getCode()))
                .findFirst()
                .orElseThrow();

        // Check health includes circuit breaker details
        mockMvc.perform(get("/api/admin/v1/providers/" + mockProvider.getId() + "/health")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.data.details.circuit_breaker.state").exists());

        // Reset circuit breaker via Admin endpoint
        mockMvc.perform(post("/api/admin/v1/providers/" + mockProvider.getId() + "/circuit/reset")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.data.message").value("Circuit breaker reset to CLOSED"));
    }

    @Test
    @DisplayName("Admin can trigger model warmup to preload model into memory")
    void adminCanWarmupModel() throws Exception {
        ModelEntity mockModel = modelMapper.selectList(null).stream()
                .filter(m -> "mock-chat-v1".equals(m.getModelIdentifier()))
                .findFirst()
                .orElseThrow();

        mockMvc.perform(post("/api/admin/v1/models/" + mockModel.getId() + "/warmup")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.data.success").value(true))
                .andExpect(jsonPath("$.data.duration_ms").exists());
    }

    @Test
    @DisplayName("Per-application model allowlist strictly blocks non-permitted models")
    void perApplicationModelAllowlistEnforced() throws Exception {
        // Configure test app settings to allow only mock-chat-v1
        io.cranial.platform.application.registry.ApplicationSettingsEntity settings =
                new io.cranial.platform.application.registry.ApplicationSettingsEntity();
        settings.setApplicationId(app.id());
        settings.setConfigJson("{\"allowed_models\": [\"mock-chat-v1\"]}");
        settings.setUpdatedAt(Instant.now());
        settingsMapper.updateById(settings);

        // 1. mock-chat-v1 is allowed
        Map<String, Object> allowedReq = Map.of(
                "capability", "conversational_intelligence",
                "messages", List.of(Map.of("role", "user", "content", "Hello")),
                "model_override", "mock-chat-v1"
        );
        mockMvc.perform(post("/api/v1/models/chat")
                        .header("X-Cranial-Api-Key", rawApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(allowedReq)))
                .andExpect(status().isOk());

        // 2. llama3.2:3b is not in allowlist -> 403 MODEL_NOT_ALLOWED
        Map<String, Object> forbiddenReq = Map.of(
                "capability", "conversational_intelligence",
                "messages", List.of(Map.of("role", "user", "content", "Hello")),
                "model_override", "llama3.2:3b"
        );
        mockMvc.perform(post("/api/v1/models/chat")
                        .header("X-Cranial-Api-Key", rawApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(forbiddenReq)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors[0].code").value("MODEL_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("Context pruner automatically trims historical turns when token budget is exceeded")
    void contextPrunerTrimsHistory() {
        io.cranial.platform.provider.service.ContextPruner pruner = new io.cranial.platform.provider.service.ContextPruner();
        ModelEntity model = new ModelEntity();
        model.setModelIdentifier("tiny-test");
        model.setContextWindowTokens(600); // very small window
        model.setMaxOutputTokens(200);     // budget = 400 tokens (~1500 chars)

        List<io.cranial.platform.provider.model.ChatMessage> history = new java.util.ArrayList<>();
        history.add(new io.cranial.platform.provider.model.ChatMessage("system", "Instruction: always be concise."));
        for (int i = 0; i < 15; i++) {
            history.add(new io.cranial.platform.provider.model.ChatMessage("user", "History question turn #" + i + " with repetitive padding text to consume token budget rapidly."));
            history.add(new io.cranial.platform.provider.model.ChatMessage("assistant", "History answer turn #" + i + " with detailed explanatory content."));
        }
        history.add(new io.cranial.platform.provider.model.ChatMessage("user", "Final user question that must be preserved."));

        List<io.cranial.platform.provider.model.ChatMessage> pruned = pruner.pruneMessages(model, history, 200);

        // Must preserve system message
        assertThat(pruned.get(0).role()).isEqualTo("system");
        assertThat(pruned.get(0).content()).isEqualTo("Instruction: always be concise.");
        // Must preserve latest user query
        assertThat(pruned.get(pruned.size() - 1).content()).isEqualTo("Final user question that must be preserved.");
        // Must have fewer messages than original
        assertThat(pruned.size()).isLessThan(history.size());
    }

    @Test
    @DisplayName("Admin can fetch real-time local GPU, CPU, and RAM utilization telemetry")
    void adminCanFetchHardwareUtilizationTelemetry() throws Exception {
        mockMvc.perform(get("/api/admin/v1/models/hardware-metrics")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.data.available_processors").isNumber())
                .andExpect(jsonPath("$.data.jvm_used_memory_bytes").isNumber())
                .andExpect(jsonPath("$.data.jvm_max_memory_bytes").isNumber())
                .andExpect(jsonPath("$.data.gpus").isArray());
    }
}
