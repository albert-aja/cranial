package io.cranial.platform.orchestration.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cranial.platform.orchestration.entity.SchemaEntity;
import io.cranial.platform.orchestration.entity.ToolEntity;
import io.cranial.platform.orchestration.mapper.SchemaMapper;
import io.cranial.platform.orchestration.model.SchemaValidationResult;
import io.cranial.platform.orchestration.model.ToolExecutionRequest;
import io.cranial.platform.orchestration.model.ToolExecutionResult;
import io.cranial.platform.orchestration.schema.SchemaValidator;
import io.cranial.platform.platform.exception.CranialException;
import io.cranial.platform.platform.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class ToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(ToolExecutor.class);

    private final SchemaMapper schemaMapper;
    private final SchemaValidator schemaValidator;
    private final ObjectMapper objectMapper;

    public ToolExecutor(SchemaMapper schemaMapper, SchemaValidator schemaValidator, ObjectMapper objectMapper) {
        this.schemaMapper = schemaMapper;
        this.schemaValidator = schemaValidator;
        this.objectMapper = objectMapper;
    }

    public ToolExecutionResult execute(ToolEntity tool, ToolExecutionRequest request) {
        long startTime = System.currentTimeMillis();

        // 1. Safety check: Peggy / money management boundaries
        // CRANIAL is purely a money management platform integration, never a banking or payment execution app.
        String toolCodeLower = tool.getToolCode().toLowerCase();
        if (toolCodeLower.contains("payment") || toolCodeLower.contains("transfer") || toolCodeLower.contains("disburse")
                || toolCodeLower.contains("withdraw") || toolCodeLower.contains("bank_wire") || toolCodeLower.contains("execute_transaction")) {
            throw new CranialException(
                    ErrorCode.TOOL_PERMISSION_DENIED,
                    "Tool execution prohibited: CRANIAL is strictly a money management integration platform and does not handle payments, bank transfers, or funds movement."
            );
        }

        // 2. Validate input against input schema if defined
        if (tool.getInputSchemaId() != null) {
            SchemaEntity inSchema = schemaMapper.selectById(tool.getInputSchemaId());
            if (inSchema != null) {
                SchemaValidationResult valResult = schemaValidator.validate(inSchema.getSchemaDefinitionJson(), request.parameters());
                if (!valResult.valid()) {
                    long duration = System.currentTimeMillis() - startTime;
                    return ToolExecutionResult.failure(
                            tool.getToolCode(),
                            "Tool input schema validation failed: " + String.join("; ", valResult.errors()),
                            duration
                    );
                }
            }
        }

        // 3. Execution by type
        Map<String, Object> outputData;
        try {
            if ("INTERNAL_MOCK".equalsIgnoreCase(tool.getExecutionType())) {
                outputData = executeMockTool(tool, request.parameters());
            } else if ("HTTP_WEBHOOK".equalsIgnoreCase(tool.getExecutionType())) {
                outputData = executeWebhookTool(tool, request.parameters());
            } else {
                throw new CranialException(
                        ErrorCode.TOOL_EXECUTION_FAILED,
                        "Unsupported tool execution type: " + tool.getExecutionType()
                );
            }
        } catch (Exception ex) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Tool {} execution failed: {}", tool.getToolCode(), ex.getMessage());
            return ToolExecutionResult.failure(tool.getToolCode(), ex.getMessage(), duration);
        }

        // 4. Validate output against output schema if defined
        if (tool.getOutputSchemaId() != null) {
            SchemaEntity outSchema = schemaMapper.selectById(tool.getOutputSchemaId());
            if (outSchema != null) {
                SchemaValidationResult valResult = schemaValidator.validate(outSchema.getSchemaDefinitionJson(), outputData);
                if (!valResult.valid()) {
                    long duration = System.currentTimeMillis() - startTime;
                    return ToolExecutionResult.failure(
                            tool.getToolCode(),
                            "Tool output schema validation failed: " + String.join("; ", valResult.errors()),
                            duration
                    );
                }
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        return ToolExecutionResult.success(tool.getToolCode(), outputData, duration);
    }

    private Map<String, Object> executeMockTool(ToolEntity tool, Map<String, Object> params) {
        String toolCode = tool.getToolCode().toLowerCase();
        Map<String, Object> result = new LinkedHashMap<>();

        if ("peggy_get_account_balance".equals(toolCode)) {
            String acc = params != null ? params.getOrDefault("account_ref", "ACC-DEFAULT").toString() : "ACC-DEFAULT";
            result.put("account_ref", acc);
            result.put("currency", "IDR");
            result.put("current_balance", 15450000.0);
            result.put("as_of", Instant.now().toString());
            return result;
        }

        // SMS: Store daily sales
        if ("sms_get_daily_sales".equals(toolCode)) {
            result.put("date", "2026-10-31");
            result.put("currency", "IDR");
            result.put("gross_sales", 18750000.0);
            result.put("net_sales", 16800000.0);
            result.put("total_transactions", 142);
            result.put("average_order_value", 132042.25);
            result.put("top_selling_category", "Groceries & Daily Essentials");
            return result;
        }

        // SMS: Low stock inventory
        if ("sms_get_low_stock_items".equals(toolCode)) {
            String wh = params != null ? params.getOrDefault("warehouse_ref", "WH-JKT-01").toString() : "WH-JKT-01";
            result.put("warehouse_ref", wh);
            result.put("items", java.util.List.of(
                    Map.of("sku", "BMR-001", "name", "Beras Pandan Wangi 5kg", "current_stock", 8, "min_threshold", 25, "unit", "pack"),
                    Map.of("sku", "MYK-002", "name", "Minyak Goreng Sawit 2L", "current_stock", 14, "min_threshold", 30, "unit", "pouch"),
                    Map.of("sku", "GLA-003", "name", "Gula Pasir Kristal 1kg", "current_stock", 19, "min_threshold", 40, "unit", "kg")
            ));
            result.put("total_critical_items", 3);
            return result;
        }

        // Worker App: Assigned tasks
        if ("worker_get_assigned_tasks".equals(toolCode)) {
            String worker = params != null ? params.getOrDefault("worker_ref", "worker1@store.test").toString() : "worker1@store.test";
            result.put("worker_ref", worker);
            result.put("assigned_date", "2026-10-31");
            result.put("tasks", java.util.List.of(
                    Map.of("task_id", "TSK-801", "type", "DELIVERY", "destination", "Jl. Sudirman No. 45, Jakarta", "status", "ASSIGNED", "priority", "HIGH"),
                    Map.of("task_id", "TSK-802", "type", "PICKUP", "destination", "Gudang Logistik Blok B, Jakarta", "status", "IN_PROGRESS", "priority", "MEDIUM")
            ));
            return result;
        }

        // Worker App: Delivery manifest
        if ("worker_get_delivery_manifest".equals(toolCode)) {
            String delId = params != null ? params.getOrDefault("delivery_id", "DEL-1001").toString() : "DEL-1001";
            result.put("delivery_id", delId);
            result.put("recipient_name", "Toko Sinar Rejeki");
            result.put("recipient_phone", "+6281298765432");
            result.put("package_count", 5);
            result.put("weight_kg", 24.5);
            result.put("special_instructions", "Drop off at rear loading bay before 16:00 WIB.");
            return result;
        }

        // Peggy Money Management: Budget rule breakdown (STRICTLY NON-BANKING)
        if ("peggy_get_budget_breakdown".equals(toolCode)) {
            result.put("app_context", "Peggy Personal Money Management (Purely Non-Banking)");
            result.put("currency", "IDR");
            result.put("monthly_net_income", 15000000.0);
            result.put("budget_rule", "50-30-20 Rule");
            result.put("categories", java.util.List.of(
                    Map.of("category", "Needs (50%)", "allocated", 7500000.0, "actual_spent", 6850000.0, "remaining", 650000.0, "status", "ON_TRACK"),
                    Map.of("category", "Wants (30%)", "allocated", 4500000.0, "actual_spent", 4920000.0, "remaining", -420000.0, "status", "OVERBUDGET"),
                    Map.of("category", "Savings & Emergency Fund (20%)", "allocated", 3000000.0, "actual_spent", 3000000.0, "remaining", 0.0, "status", "ACHIEVED")
            ));
            return result;
        }

        // Peggy Money Management: Monthly categorized spending (STRICTLY NON-BANKING)
        if ("peggy_get_monthly_spending".equals(toolCode)) {
            result.put("period", "2026-10");
            result.put("currency", "IDR");
            result.put("total_expenses", 11770000.0);
            result.put("breakdown", java.util.List.of(
                    Map.of("category", "Groceries", "amount", 3850000.0, "percent", 32.7),
                    Map.of("category", "Dining Out", "amount", 2250000.0, "percent", 19.1),
                    Map.of("category", "Utilities & Internet", "amount", 1650000.0, "percent", 14.0),
                    Map.of("category", "Transport", "amount", 1350000.0, "percent", 11.5),
                    Map.of("category", "Health & Wellness", "amount", 950000.0, "percent", 8.1),
                    Map.of("category", "Others", "amount", 1720000.0, "percent", 14.6)
            ));
            return result;
        }

        // E-Commerce: Product catalog info
        if ("ecom_get_product_info".equals(toolCode)) {
            String sku = params != null ? params.getOrDefault("sku", "ECOM-CHAIR-01").toString() : "ECOM-CHAIR-01";
            result.put("sku", sku);
            result.put("name", "Ergonomic Office Chair Mesh Pro");
            result.put("currency", "IDR");
            result.put("price", 1850000.0);
            result.put("stock_status", "IN_STOCK");
            result.put("stock_count", 42);
            result.put("rating", 4.8);
            result.put("description", "Breathable mesh back with lumbar support and 3D adjustable armrests.");
            return result;
        }

        // E-Commerce: Order status
        if ("ecom_get_order_status".equals(toolCode)) {
            String ord = params != null ? params.getOrDefault("order_id", "ORD-2026-9901").toString() : "ORD-2026-9901";
            result.put("order_id", ord);
            result.put("status", "IN_TRANSIT");
            result.put("courier", "JNE Express");
            result.put("tracking_number", "JNE88291039401");
            result.put("estimated_delivery", "2026-11-02");
            result.put("last_location", "Jakarta Distribution Hub");
            return result;
        }

        // Generic mock response echoing parameters safely
        result.put("tool", tool.getToolCode());
        result.put("status", "mock_completed");
        result.put("received_parameters", params);
        result.put("timestamp", Instant.now().toString());
        return result;
    }

    private Map<String, Object> executeWebhookTool(ToolEntity tool, Map<String, Object> params) {
        if (tool.getEndpointUrl() == null || tool.getEndpointUrl().isBlank()) {
            throw new CranialException(ErrorCode.TOOL_EXECUTION_FAILED, "Tool endpoint_url is empty");
        }

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        int timeout = tool.getTimeoutMs() != null ? tool.getTimeoutMs() : 5000;
        factory.setConnectTimeout(Duration.ofMillis(Math.min(timeout, 3000)));
        factory.setReadTimeout(Duration.ofMillis(timeout));

        RestClient client = RestClient.builder()
                .requestFactory(factory)
                .build();

        String responseStr = client.post()
                .uri(tool.getEndpointUrl())
                .contentType(MediaType.APPLICATION_JSON)
                .body(params)
                .retrieve()
                .body(String.class);

        try {
            return objectMapper.readValue(responseStr, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            return Map.of("raw_response", responseStr != null ? responseStr : "");
        }
    }
}

