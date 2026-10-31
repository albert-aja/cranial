package io.cranial.platform.provider.adapter;

import io.cranial.platform.provider.entity.ModelEntity;
import io.cranial.platform.provider.entity.ProviderEntity;
import io.cranial.platform.provider.model.ChatCompletionRequest;
import io.cranial.platform.provider.model.ChatCompletionResponse;
import io.cranial.platform.provider.model.ChatMessage;
import io.cranial.platform.provider.model.EmbeddingRequest;
import io.cranial.platform.provider.model.EmbeddingResponse;
import io.cranial.platform.provider.model.ProviderHealth;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class MockLocalProviderAdapter implements ModelProviderAdapter {

    public static final String PROVIDER_CODE = "mock_local";

    @Override
    public String providerCode() {
        return PROVIDER_CODE;
    }

    @Override
    public ProviderHealth checkHealth(ProviderEntity provider) {
        return ProviderHealth.healthy(PROVIDER_CODE, 1L, "Mock local engine ready (in-memory)", Map.of("mode", "mock_local"));
    }

    @Override
    public CompletableFuture<ChatCompletionResponse> chat(
            ProviderEntity provider,
            ModelEntity model,
            ChatCompletionRequest request
    ) {
        return CompletableFuture.supplyAsync(() -> {
            long start = System.currentTimeMillis();
            String userPrompt = request.messages().isEmpty() ? "" : request.messages().get(request.messages().size() - 1).content();
            String responseText;
            if (userPrompt.contains("MOCK_JSON:")) {
                responseText = userPrompt.substring(userPrompt.indexOf("MOCK_JSON:") + "MOCK_JSON:".length()).trim();
            } else if (userPrompt.contains("Tool output:")) {
                responseText = """
                        {
                          "summary": "Synthesized tool output into financial summary",
                          "key_takeaways": ["Balance verified", "Maintain healthy savings"]
                        }
                        """;
            } else if ("financial_summary".equalsIgnoreCase(request.task()) || userPrompt.contains("pembukuan") || userPrompt.contains("financial ledger")) {
                boolean isId = request.locale() != null && (request.locale().startsWith("id") || request.locale().startsWith("in"));
                if (isId) {
                    responseText = """
                            {
                              "summary": "Ringkasan analisis keuangan pengguna untuk manajemen keuangan sehat.",
                              "key_takeaways": ["Pengeluaran tercatat dengan rapi", "Pertahankan rasio tabungan positif"]
                            }
                            """;
                } else {
                    responseText = """
                            {
                              "summary": "Financial education summary for healthy money management.",
                              "key_takeaways": ["Ledger entries reviewed", "Maintain emergency fund"]
                            }
                            """;
                }
            } else if ("rag_grounded_answer".equalsIgnoreCase(request.task())) {
                boolean isId = request.locale() != null && (request.locale().startsWith("id") || request.locale().startsWith("in"));
                String snippet = extractGroundedSnippet(userPrompt);
                if (isId) {
                    responseText = "Berdasarkan sumber pengetahuan terotorisasi: " + snippet + " [C1]";
                } else {
                    responseText = "Based on authorized knowledge evidence: " + snippet + " [C1]";
                }
            } else {
                responseText = "CRANIAL Mock Response: Processed capability '" + request.capability() + "' for input: " + userPrompt;
            }

            int promptTokens = Math.max(10, userPrompt.length() / 4);
            int completionTokens = Math.max(15, responseText.length() / 4);
            int totalTokens = promptTokens + completionTokens;
            long duration = Math.max(5L, System.currentTimeMillis() - start);

            return new ChatCompletionResponse(
                    UUID.randomUUID().toString(),
                    model.getModelIdentifier(),
                    PROVIDER_CODE,
                    new ChatMessage("assistant", responseText),
                    promptTokens,
                    completionTokens,
                    totalTokens,
                    duration,
                    true,
                    false,
                    Map.of("mock_execution", true, "engine", "mock_local")
            );
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
            List<List<Double>> embeddings = new ArrayList<>();
            int totalPromptTokens = 0;

            for (String input : request.inputs()) {
                String safe = input != null ? input : "";
                totalPromptTokens += Math.max(5, safe.length() / 4);
                embeddings.add(computeDeterministicSemanticVector(safe, 64));
            }

            long duration = Math.max(2L, System.currentTimeMillis() - start);
            return new EmbeddingResponse(
                    model.getModelIdentifier(),
                    PROVIDER_CODE,
                    embeddings,
                    totalPromptTokens,
                    totalPromptTokens,
                    duration,
                    true
            );
        });
    }

    private static final Map<String, String> BILINGUAL_CONCEPT_ALIASES = Map.ofEntries(
            Map.entry("dana", "fund"),
            Map.entry("darurat", "emergency"),
            Map.entry("anggaran", "budget"),
            Map.entry("inflasi", "inflation"),
            Map.entry("bunga", "interest"),
            Map.entry("majemuk", "compound"),
            Map.entry("arus", "flow"),
            Map.entry("kas", "cash"),
            Map.entry("tabungan", "savings"),
            Map.entry("pengeluaran", "expense"),
            Map.entry("pendapatan", "income"),
            Map.entry("gudang", "warehouse"),
            Map.entry("pengiriman", "delivery"),
            Map.entry("barang", "inventory"),
            Map.entry("stok", "stock"),
            Map.entry("laba", "profit"),
            Map.entry("gaji", "payroll"),
            Map.entry("karyawan", "worker"),
            Map.entry("keselamatan", "safety"),
            Map.entry("prosedur", "procedure"),
            Map.entry("pengembalian", "refund")
    );

    public static List<Double> computeDeterministicSemanticVector(String text, int dims) {
        double[] vec = new double[dims];
        String norm = text == null ? "" : text.toLowerCase(java.util.Locale.ROOT).replaceAll("[^\\p{L}\\p{N}\\s]", " ");
        String[] tokens = norm.trim().split("\\s+");

        for (String rawTok : tokens) {
            if (rawTok.length() < 2) {
                continue;
            }
            projectFeature(vec, "tok:" + rawTok, 2.0);
            String canonical = BILINGUAL_CONCEPT_ALIASES.get(rawTok);
            if (canonical != null) {
                projectFeature(vec, "tok:" + canonical, 2.2);
            }
            if (rawTok.length() > 4 && rawTok.endsWith("ing")) {
                projectFeature(vec, "stem:" + rawTok.substring(0, rawTok.length() - 3), 1.2);
            } else if (rawTok.length() > 3 && rawTok.endsWith("s")) {
                projectFeature(vec, "stem:" + rawTok.substring(0, rawTok.length() - 1), 1.2);
            }
            for (int i = 0; i <= rawTok.length() - 3; i++) {
                projectFeature(vec, "tri:" + rawTok.substring(i, i + 3), 0.45);
            }
        }

        double sumSq = 0.0;
        for (double v : vec) {
            sumSq += v * v;
        }
        List<Double> out = new ArrayList<>(dims);
        if (sumSq < 1e-12) {
            out.add(1.0);
            for (int i = 1; i < dims; i++) {
                out.add(0.0);
            }
            return out;
        }
        double invNorm = 1.0 / Math.sqrt(sumSq);
        for (double v : vec) {
            out.add(Math.round(v * invNorm * 1_000_000.0) / 1_000_000.0);
        }
        return out;
    }

    private static void projectFeature(double[] vec, String feature, double weight) {
        int h1 = fnv1a(feature, 0x811c9dc5);
        int h2 = fnv1a(feature, 0x9e3779b9);
        int idx1 = Math.floorMod(h1, vec.length);
        int idx2 = Math.floorMod(h2, vec.length);
        double sign1 = ((h1 & 1) == 0) ? 1.0 : -1.0;
        double sign2 = ((h2 & 2) == 0) ? 1.0 : -1.0;
        vec[idx1] += sign1 * weight;
        vec[idx2] += sign2 * (weight * 0.5);
    }

    private static int fnv1a(String s, int seed) {
        int h = seed;
        for (int i = 0; i < s.length(); i++) {
            h ^= s.charAt(i);
            h *= 0x01000193;
        }
        return h;
    }

    private static String extractGroundedSnippet(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return "No evidence snippet.";
        }
        int idx = prompt.indexOf("[C1]");
        String slice = idx >= 0 ? prompt.substring(idx) : prompt;
        String[] lines = slice.split("\\R");
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("[C2]") || trimmed.startsWith("USER_QUESTION:")) {
                if (!sb.isEmpty()) {
                    break;
                }
                continue;
            }
            if (!trimmed.startsWith("[C1]")) {
                if (!sb.isEmpty()) {
                    sb.append(' ');
                }
                sb.append(trimmed);
            }
        }
        String out = sb.toString().replaceAll("^#+\\s*", "").trim();
        if (out.isEmpty()) {
            out = slice.replaceAll("\\s+", " ").trim();
        }
        return out.length() > 360 ? out.substring(0, 360) + "..." : out;
    }

    @Override
    public CompletableFuture<Void> streamChat(
            ProviderEntity provider,
            ModelEntity model,
            ChatCompletionRequest request,
            java.util.function.Consumer<String> chunkConsumer,
            java.util.function.Consumer<ChatCompletionResponse> completionConsumer
    ) {
        return chat(provider, model, request).thenAccept(resp -> {
            String content = resp.message().content();
            String[] tokens = content.split("(?<=\\s)|(?=\\s)");
            for (String tok : tokens) {
                chunkConsumer.accept(tok);
            }
            completionConsumer.accept(resp);
        });
    }
}

