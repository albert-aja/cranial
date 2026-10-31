package io.cranial.platform.provider.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cranial.platform.platform.exception.CranialException;
import io.cranial.platform.platform.exception.ErrorCode;
import io.cranial.platform.provider.entity.ModelEntity;
import io.cranial.platform.provider.entity.ProviderEntity;
import io.cranial.platform.provider.model.ChatCompletionRequest;
import io.cranial.platform.provider.model.ChatCompletionResponse;
import io.cranial.platform.provider.model.ChatMessage;
import io.cranial.platform.provider.model.EmbeddingRequest;
import io.cranial.platform.provider.model.EmbeddingResponse;
import io.cranial.platform.provider.model.ProviderHealth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

@Component
public class OllamaProviderAdapter implements ModelProviderAdapter {

    private static final Logger log = LoggerFactory.getLogger(OllamaProviderAdapter.class);
    public static final String PROVIDER_CODE = "ollama";

    private final ObjectMapper objectMapper;

    public OllamaProviderAdapter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String providerCode() {
        return PROVIDER_CODE;
    }

    @Override
    public ProviderHealth checkHealth(ProviderEntity provider) {
        long start = System.currentTimeMillis();
        try {
            RestClient client = buildRestClient(provider, 5);
            String response = client.get()
                    .uri("/api/version")
                    .retrieve()
                    .body(String.class);

            long latency = System.currentTimeMillis() - start;
            JsonNode root = objectMapper.readTree(response);
            String version = root.has("version") ? root.get("version").asText() : "unknown";

            Map<String, Object> details = new LinkedHashMap<>();
            details.put("version", version);

            // Query /api/ps for running models and GPU VRAM diagnostics
            try {
                String psResponse = client.get()
                        .uri("/api/ps")
                        .retrieve()
                        .body(String.class);
                if (psResponse != null && !psResponse.isBlank()) {
                    JsonNode psRoot = objectMapper.readTree(psResponse);
                    if (psRoot.has("models") && psRoot.get("models").isArray()) {
                        List<Map<String, Object>> modelsList = new ArrayList<>();
                        long totalVram = 0;
                        for (JsonNode m : psRoot.get("models")) {
                            Map<String, Object> minfo = new LinkedHashMap<>();
                            minfo.put("name", m.path("name").asText());
                            long vram = m.path("size_vram").asLong(0);
                            minfo.put("size_vram_bytes", vram);
                            totalVram += vram;
                            modelsList.add(minfo);
                        }
                        details.put("models_loaded", modelsList);
                        details.put("total_vram_bytes", totalVram);
                        details.put("gpu_offload_status", totalVram > 0 ? "GPU_ACTIVE" : "CPU_ONLY");
                    }
                }
            } catch (Exception ignored) {
                details.put("gpu_diagnostics", "Ollama /api/ps unavailable");
            }

            return ProviderHealth.healthy(PROVIDER_CODE, latency, "Ollama operational (v" + version + ")", details);
        } catch (Exception ex) {
            long latency = System.currentTimeMillis() - start;
            log.warn("Ollama health check failed for {}: {}", provider.getBaseUrl(), ex.getMessage());
            return ProviderHealth.unreachable(PROVIDER_CODE, latency, "Ollama unreachable at " + provider.getBaseUrl() + ": " + ex.getMessage());
        }
    }

    @Override
    public CompletableFuture<ChatCompletionResponse> chat(
            ProviderEntity provider,
            ModelEntity model,
            ChatCompletionRequest request
    ) {
        return CompletableFuture.supplyAsync(() -> {
            long start = System.currentTimeMillis();
            try {
                RestClient client = buildRestClient(provider, provider.getTimeoutSeconds());

                Map<String, Object> body = new LinkedHashMap<>();
                body.put("model", model.getModelIdentifier());
                body.put("stream", false);

                List<Map<String, String>> messagesList = new ArrayList<>();
                for (ChatMessage m : request.messages()) {
                    messagesList.add(Map.of("role", m.role(), "content", m.content()));
                }
                body.put("messages", messagesList);

                Map<String, Object> options = new LinkedHashMap<>();
                if (request.temperature() != null) {
                    options.put("temperature", request.temperature());
                }
                if (request.maxTokens() != null) {
                    options.put("num_predict", request.maxTokens());
                } else if (model.getMaxOutputTokens() != null && model.getMaxOutputTokens() > 0) {
                    options.put("num_predict", model.getMaxOutputTokens());
                }
                if (!options.isEmpty()) {
                    body.put("options", options);
                }

                String responseStr = client.post()
                        .uri("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(String.class);

                long duration = System.currentTimeMillis() - start;
                JsonNode root = objectMapper.readTree(responseStr);

                String content = "";
                String role = "assistant";
                if (root.has("message")) {
                    JsonNode msgNode = root.get("message");
                    content = msgNode.has("content") ? msgNode.get("content").asText() : "";
                    role = msgNode.has("role") ? msgNode.get("role").asText() : "assistant";
                }

                int promptTokens = root.has("prompt_eval_count") ? root.get("prompt_eval_count").asInt() : 0;
                int completionTokens = root.has("eval_count") ? root.get("eval_count").asInt() : 0;
                int totalTokens = promptTokens + completionTokens;

                return new ChatCompletionResponse(
                        UUID.randomUUID().toString(),
                        model.getModelIdentifier(),
                        PROVIDER_CODE,
                        new ChatMessage(role, content),
                        promptTokens,
                        completionTokens,
                        totalTokens,
                        duration,
                        true,
                        false,
                        Map.of("ollama_done", root.has("done") && root.get("done").asBoolean())
                );
            } catch (Exception ex) {
                long duration = System.currentTimeMillis() - start;
                log.error("Ollama chat completion call failed: {}", ex.getMessage());
                throw new CranialException(
                        ErrorCode.PROVIDER_UNAVAILABLE,
                        "Ollama provider failed at " + provider.getBaseUrl() + ": " + ex.getMessage()
                );
            }
        });
    }

    @Override
    public CompletableFuture<EmbeddingResponse> embed(
            ProviderEntity provider,
            ModelEntity model,
            EmbeddingRequest request
    ) {
        return CompletableFuture.supplyAsync(() -> {
            long start = System.currentTimeMillis();
            try {
                RestClient client = buildRestClient(provider, provider.getTimeoutSeconds());

                Map<String, Object> body = new LinkedHashMap<>();
                body.put("model", model.getModelIdentifier());
                body.put("input", request.inputs());

                String responseStr = client.post()
                        .uri("/api/embed")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(String.class);

                long duration = System.currentTimeMillis() - start;
                JsonNode root = objectMapper.readTree(responseStr);

                List<List<Double>> embeddingsList = new ArrayList<>();
                if (root.has("embeddings")) {
                    for (JsonNode vecNode : root.get("embeddings")) {
                        List<Double> vec = new ArrayList<>();
                        for (JsonNode val : vecNode) {
                            vec.add(val.asDouble());
                        }
                        embeddingsList.add(vec);
                    }
                }

                int promptTokens = root.has("prompt_eval_count") ? root.get("prompt_eval_count").asInt() : 0;

                return new EmbeddingResponse(
                        model.getModelIdentifier(),
                        PROVIDER_CODE,
                        embeddingsList,
                        promptTokens,
                        promptTokens,
                        duration,
                        true
                );
            } catch (Exception ex) {
                log.error("Ollama embed call failed: {}", ex.getMessage());
                throw new CranialException(
                        ErrorCode.PROVIDER_UNAVAILABLE,
                        "Ollama embedding failed at " + provider.getBaseUrl() + ": " + ex.getMessage()
                );
            }
        });
    }

    @Override
    public CompletableFuture<Void> streamChat(
            ProviderEntity provider,
            ModelEntity model,
            ChatCompletionRequest request,
            Consumer<String> chunkConsumer,
            Consumer<ChatCompletionResponse> completionConsumer
    ) {
        return CompletableFuture.runAsync(() -> {
            long start = System.currentTimeMillis();
            try {
                RestClient client = buildRestClient(provider, provider.getTimeoutSeconds());

                Map<String, Object> body = new LinkedHashMap<>();
                body.put("model", model.getModelIdentifier());
                body.put("stream", true);

                List<Map<String, String>> messagesList = new ArrayList<>();
                for (ChatMessage m : request.messages()) {
                    messagesList.add(Map.of("role", m.role(), "content", m.content()));
                }
                body.put("messages", messagesList);

                Map<String, Object> options = new LinkedHashMap<>();
                if (request.temperature() != null) {
                    options.put("temperature", request.temperature());
                }
                if (request.maxTokens() != null) {
                    options.put("num_predict", request.maxTokens());
                } else if (model.getMaxOutputTokens() != null && model.getMaxOutputTokens() > 0) {
                    options.put("num_predict", model.getMaxOutputTokens());
                }
                if (!options.isEmpty()) {
                    body.put("options", options);
                }

                client.post()
                        .uri("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .exchange((req, res) -> {
                            try (BufferedReader reader = new BufferedReader(new InputStreamReader(res.getBody(), StandardCharsets.UTF_8))) {
                                String line;
                                StringBuilder fullContent = new StringBuilder();
                                int promptTokens = 0;
                                int completionTokens = 0;

                                while ((line = reader.readLine()) != null) {
                                    if (line.isBlank()) continue;
                                    JsonNode node = objectMapper.readTree(line);
                                    if (node.has("message") && node.get("message").has("content")) {
                                        String delta = node.get("message").get("content").asText();
                                        fullContent.append(delta);
                                        chunkConsumer.accept(delta);
                                    }
                                    if (node.has("done") && node.get("done").asBoolean()) {
                                        promptTokens = node.path("prompt_eval_count").asInt(0);
                                        completionTokens = node.path("eval_count").asInt(0);
                                    }
                                }

                                long duration = System.currentTimeMillis() - start;
                                ChatCompletionResponse finalResp = new ChatCompletionResponse(
                                        UUID.randomUUID().toString(),
                                        model.getModelIdentifier(),
                                        PROVIDER_CODE,
                                        new ChatMessage("assistant", fullContent.toString()),
                                        promptTokens,
                                        completionTokens,
                                        promptTokens + completionTokens,
                                        duration,
                                        true,
                                        false,
                                        Map.of("streamed", true)
                                );
                                completionConsumer.accept(finalResp);
                            }
                            return null;
                        });
            } catch (Exception ex) {
                log.error("Ollama streaming chat failed: {}", ex.getMessage());
                throw new CranialException(
                        ErrorCode.PROVIDER_UNAVAILABLE,
                        "Ollama streaming chat failed: " + ex.getMessage()
                );
            }
        });
    }

    private RestClient buildRestClient(ProviderEntity provider, int timeoutSeconds) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(Math.min(timeoutSeconds, 10)));
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));

        return RestClient.builder()
                .baseUrl(provider.getBaseUrl())
                .requestFactory(factory)
                .build();
    }
}
