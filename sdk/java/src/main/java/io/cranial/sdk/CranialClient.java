package io.cranial.sdk;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class CranialClient {

    private final CranialConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public CranialClient(CranialConfig config) {
        this(config, HttpClient.newBuilder().connectTimeout(config.timeout()).build(), new ObjectMapper());
    }

    public CranialClient(CranialConfig config, HttpClient httpClient, ObjectMapper objectMapper) {
        this.config = config;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    public static CranialClient create(CranialConfig config) {
        return new CranialClient(config);
    }

    public static CranialClient create(String baseUrl, String apiKey, String signingSecret) {
        return new CranialClient(CranialConfig.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .signingSecret(signingSecret)
                .build());
    }

    /**
     * Probe platform gateway health and clock synchronization.
     */
    public Map<String, Object> checkHealth() {
        return sendRequest("GET", "/api/v1/integration/health", null, null, new TypeReference<Map<String, Object>>() {});
    }

    /**
     * Echo verified signed-context claims and HMAC signature calculation.
     */
    public Map<String, Object> echo(SignedContext ctx, Object payload) {
        return sendRequest("POST", "/api/v1/integration/echo", payload, ctx, new TypeReference<Map<String, Object>>() {});
    }

    /**
     * List authorized tasks, safe tools, and knowledge collections for this caller application.
     */
    public Map<String, Object> getCatalog(SignedContext ctx) {
        return sendRequest("GET", "/api/v1/integration/catalog", null, ctx, new TypeReference<Map<String, Object>>() {});
    }

    /**
     * Execute an AI Orchestration task (Phase 3).
     */
    public Map<String, Object> executeTask(String taskCode, Map<String, Object> inputs, SignedContext ctx) {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("task_code", taskCode);
        req.put("inputs", inputs != null ? inputs : Map.of());
        req.put("allow_tools", true);
        return sendRequest("POST", "/api/v1/orchestrator/execute", req, ctx, new TypeReference<Map<String, Object>>() {});
    }

    /**
     * Execute model chat completion (Phase 2).
     */
    public Map<String, Object> chat(Map<String, Object> chatRequest, SignedContext ctx) {
        return sendRequest("POST", "/api/v1/models/chat", chatRequest, ctx, new TypeReference<Map<String, Object>>() {});
    }

    /**
     * Search and retrieve knowledge chunks with strict permission enforcement (Phase 6).
     */
    public Map<String, Object> retrieveKnowledge(String collectionCode, String query, int limit, SignedContext ctx) {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("collection_code", collectionCode);
        req.put("query", query);
        req.put("limit", limit > 0 ? limit : 5);
        return sendRequest("POST", "/api/v1/knowledge/retrieve", req, ctx, new TypeReference<Map<String, Object>>() {});
    }

    /**
     * Ask a grounded question against authorized knowledge collections with source citations (Phase 6).
     */
    public Map<String, Object> askKnowledge(String collectionCode, String question, SignedContext ctx) {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("collection_code", collectionCode);
        req.put("question", question);
        return sendRequest("POST", "/api/v1/knowledge/ask", req, ctx, new TypeReference<Map<String, Object>>() {});
    }

    /**
     * Query deterministic analytics and reporting data (Phase 7).
     */
    public Map<String, Object> queryAnalytics(String queryType, String datasetCode, Map<String, Object> filters, SignedContext ctx) {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("query_type", queryType);
        req.put("dataset_code", datasetCode);
        req.put("filters", filters != null ? filters : Map.of());
        return sendRequest("POST", "/api/v1/analytics/query", req, ctx, new TypeReference<Map<String, Object>>() {});
    }

    /**
     * Submit raw document for OCR and structured extraction preview (Phase 5).
     * Note: Money management apps like Peggy receive read-only preview only, never banking execution.
     */
    public Map<String, Object> submitDocumentText(String textContent, String documentType, SignedContext ctx) {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("document_type", documentType);
        req.put("raw_text", textContent);
        return sendRequest("POST", "/api/v1/documents", req, ctx, new TypeReference<Map<String, Object>>() {});
    }

    private <T> T sendRequest(String method, String path, Object bodyObj, SignedContext ctx, TypeReference<T> responseType) {
        try {
            byte[] rawBody = bodyObj != null ? objectMapper.writeValueAsBytes(bodyObj) : new byte[0];
            String uriStr = config.baseUrl() + path;

            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(uriStr))
                    .timeout(config.timeout())
                    .header("X-Cranial-Api-Key", config.apiKey())
                    .header("Accept", "application/json");

            if (ctx != null && config.signingSecret() != null) {
                SignedContext.SignedHeaders signedHeaders = ctx.generateHeaders(method, path, rawBody, config.signingSecret());
                reqBuilder.header("X-Cranial-Context", signedHeaders.contextHeader());
                reqBuilder.header("X-Cranial-Timestamp", signedHeaders.timestampHeader());
                reqBuilder.header("X-Cranial-Signature", signedHeaders.signatureHeader());
            }

            if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method) || "PATCH".equalsIgnoreCase(method)) {
                reqBuilder.header("Content-Type", "application/json");
                reqBuilder.method(method.toUpperCase(Locale.ROOT), HttpRequest.BodyPublishers.ofByteArray(rawBody));
            } else {
                reqBuilder.method(method.toUpperCase(Locale.ROOT), HttpRequest.BodyPublishers.noBody());
            }

            HttpResponse<String> response = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int statusCode = response.statusCode();
            String responseBody = response.body();

            if (statusCode >= 200 && statusCode < 300) {
                JsonNode root = objectMapper.readTree(responseBody);
                JsonNode dataNode = root.get("data");
                if (dataNode != null) {
                    return objectMapper.convertValue(dataNode, responseType);
                }
                return objectMapper.convertValue(root, responseType);
            }

            // Extract error response
            String errCode = "HTTP_" + statusCode;
            String errMsg = "Request failed with HTTP " + statusCode;
            try {
                JsonNode errRoot = objectMapper.readTree(responseBody);
                if (errRoot.has("errors") && errRoot.get("errors").isArray() && !errRoot.get("errors").isEmpty()) {
                    JsonNode firstErr = errRoot.get("errors").get(0);
                    errCode = firstErr.path("code").asText(errCode);
                    errMsg = firstErr.path("message").asText(errMsg);
                }
            } catch (Exception ignored) {}

            throw new CranialSdkException(statusCode, errCode, errMsg);
        } catch (CranialSdkException cse) {
            throw cse;
        } catch (Exception ex) {
            throw new CranialSdkException("HTTP communication with CRANIAL failed: " + ex.getMessage(), ex);
        }
    }
}

