package io.cranial.platform.orchestration.engine;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cranial.platform.orchestration.entity.OrchestratorTraceEntity;
import io.cranial.platform.orchestration.entity.PromptEntity;
import io.cranial.platform.orchestration.entity.SchemaEntity;
import io.cranial.platform.orchestration.entity.TaskDefinitionEntity;
import io.cranial.platform.orchestration.entity.ToolEntity;
import io.cranial.platform.orchestration.mapper.OrchestratorTraceMapper;
import io.cranial.platform.orchestration.mapper.PromptMapper;
import io.cranial.platform.orchestration.mapper.SchemaMapper;
import io.cranial.platform.orchestration.mapper.TaskDefinitionMapper;
import io.cranial.platform.orchestration.mapper.ToolMapper;
import io.cranial.platform.orchestration.model.OrchestrationStep;
import io.cranial.platform.orchestration.model.OrchestratorRequest;
import io.cranial.platform.orchestration.model.OrchestratorResult;
import io.cranial.platform.orchestration.model.SchemaValidationResult;
import io.cranial.platform.orchestration.model.ToolExecutionRequest;
import io.cranial.platform.orchestration.model.ToolExecutionResult;
import io.cranial.platform.orchestration.prompt.PromptInterpolator;
import io.cranial.platform.orchestration.schema.SchemaValidator;
import io.cranial.platform.orchestration.tool.ToolExecutor;
import io.cranial.platform.platform.exception.CranialException;
import io.cranial.platform.platform.exception.ErrorCode;
import io.cranial.platform.provider.model.ChatCompletionRequest;
import io.cranial.platform.provider.model.ChatCompletionResponse;
import io.cranial.platform.provider.model.ChatMessage;
import io.cranial.platform.provider.service.ModelExecutionService;
import io.cranial.platform.security.VerifiedRequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AiOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AiOrchestrator.class);
    private static final Pattern JSON_BLOCK_PATTERN = Pattern.compile("(?s)```(?:json)?\\s*(\\{.*?\\})\\s*```");
    private static final List<String> FORBIDDEN_BANKING_VERBS = List.of(
            "payment", "transfer", "disburse", "withdraw", "bank_wire", "execute_transaction"
    );

    private final TaskDefinitionMapper taskMapper;
    private final PromptMapper promptMapper;
    private final SchemaMapper schemaMapper;
    private final ToolMapper toolMapper;
    private final OrchestratorTraceMapper traceMapper;

    private final PromptInterpolator interpolator;
    private final SchemaValidator schemaValidator;
    private final ToolExecutor toolExecutor;
    private final ModelExecutionService modelExecutionService;
    private final ObjectMapper objectMapper;

    public AiOrchestrator(
            TaskDefinitionMapper taskMapper,
            PromptMapper promptMapper,
            SchemaMapper schemaMapper,
            ToolMapper toolMapper,
            OrchestratorTraceMapper traceMapper,
            PromptInterpolator interpolator,
            SchemaValidator schemaValidator,
            ToolExecutor toolExecutor,
            ModelExecutionService modelExecutionService,
            ObjectMapper objectMapper
    ) {
        this.taskMapper = taskMapper;
        this.promptMapper = promptMapper;
        this.schemaMapper = schemaMapper;
        this.toolMapper = toolMapper;
        this.traceMapper = traceMapper;
        this.interpolator = interpolator;
        this.schemaValidator = schemaValidator;
        this.toolExecutor = toolExecutor;
        this.modelExecutionService = modelExecutionService;
        this.objectMapper = objectMapper;
    }

    public OrchestratorResult execute(
            VerifiedRequestContext ctx,
            OrchestratorRequest request,
            String clientIp
    ) {
        long startTime = System.currentTimeMillis();
        UUID traceId = UUID.randomUUID();
        List<OrchestrationStep> steps = new ArrayList<>();
        int stepIndex = 0;

        TaskDefinitionEntity task = null;
        PromptEntity prompt = null;
        SchemaEntity outputSchema = null;
        String modelName = "unknown";
        UUID modelId = null;
        int totalPromptTokens = 0;
        int totalCompletionTokens = 0;
        int toolCallsCount = 0;

        try {
            // -----------------------------------------------------------------
            // Step 1: Task & Prompt Resolution
            // -----------------------------------------------------------------
            long stepStart = System.currentTimeMillis();
            task = taskMapper.findActiveByCode(request.taskCode().trim().toLowerCase())
                    .orElseThrow(() -> new CranialException(
                            ErrorCode.TASK_NOT_FOUND,
                            "Active task definition not found: " + request.taskCode()
                    ));

            String effectiveLocale = request.locale() != null ? request.locale().toLowerCase().trim() : "en";
            boolean isIndonesian = effectiveLocale.startsWith("id") || effectiveLocale.startsWith("in");

            // If Indonesian requested, prioritize published Indonesian prompt template
            if (isIndonesian) {
                prompt = promptMapper.findPublishedByCode(request.taskCode() + "_prompt_id").orElse(null);
            }
            if (prompt == null && task.getDefaultPromptId() != null) {
                prompt = promptMapper.selectById(task.getDefaultPromptId());
            }
            if (prompt == null) {
                prompt = promptMapper.findPublishedByCode(request.taskCode() + "_prompt")
                        .orElse(null);
            }
            if (prompt == null) {
                throw new CranialException(
                        ErrorCode.PROMPT_NOT_FOUND,
                        "No published prompt template found for task: " + request.taskCode()
                );
            }

            List<String> requiredVars = interpolator.parseRequiredVariables(prompt.getInputVariablesJson());
            interpolator.validateRequiredVariables(requiredVars, request.inputs());

            String systemPrompt = interpolator.interpolate(prompt.getSystemTemplate(), request.inputs());
            String userPrompt = interpolator.interpolate(prompt.getUserTemplate(), request.inputs());

            if (isIndonesian && !systemPrompt.toLowerCase().contains("bahasa indonesia")) {
                systemPrompt += "\nPetunjuk Bahasa: Berikan respons dalam Bahasa Indonesia yang formal, akurat, dan mudah dipahami.";
            }

            long stepDuration = System.currentTimeMillis() - stepStart;
            steps.add(OrchestrationStep.of(
                    ++stepIndex,
                    "Prompt Resolution",
                    "PROMPT_RESOLUTION",
                    stepDuration,
                    "SUCCESS",
                    Map.of(
                            "prompt_code", prompt.getPromptCode(),
                            "prompt_version", prompt.getVersion(),
                            "task_code", task.getTaskCode(),
                            "locale", effectiveLocale
                    )
            ));

            // -----------------------------------------------------------------
            // Step 2: Tool Registry Discovery & Context Augmentation
            // -----------------------------------------------------------------
            List<ToolEntity> availableTools = List.of();
            if (Boolean.TRUE.equals(request.allowTools())) {
                availableTools = toolMapper.findAvailableForApplication(ctx.applicationId());
            }

            // -----------------------------------------------------------------
            // Step 3: Model Dispatch via ModelExecutionService (Phase 2)
            // -----------------------------------------------------------------
            stepStart = System.currentTimeMillis();
            ChatCompletionRequest chatReq = new ChatCompletionRequest(
                    task.getCapabilityCode(),
                    task.getTaskCode(),
                    List.of(
                            ChatMessage.system(systemPrompt),
                            ChatMessage.user(userPrompt)
                    ),
                    0.2, // low temperature for structured output
                    null,
                    request.modelOverride(),
                    request.dataSensitivity(),
                    Map.of("task_id", task.getId().toString()),
                    effectiveLocale
            );

            ChatCompletionResponse chatResp = modelExecutionService.executeChat(ctx, chatReq, clientIp);
            stepDuration = System.currentTimeMillis() - stepStart;

            modelName = chatResp.model();
            totalPromptTokens += chatResp.promptTokens();
            totalCompletionTokens += chatResp.completionTokens();

            steps.add(OrchestrationStep.of(
                    ++stepIndex,
                    "Model Inference",
                    "MODEL_INFERENCE",
                    stepDuration,
                    "SUCCESS",
                    Map.of(
                            "model", chatResp.model(),
                            "provider", chatResp.provider(),
                            "is_local", chatResp.isLocal(),
                            "fallback_used", chatResp.fallbackUsed(),
                            "tokens", chatResp.totalTokens()
                    )
            ));

            String assistantMessage = chatResp.message().content();

            // -----------------------------------------------------------------
            // Step 4: Tool Execution (if model requested a tool invocation)
            // -----------------------------------------------------------------
            if (assistantMessage.contains("tool_call") || assistantMessage.contains("\"tool\"")) {
                ToolExecutionRequest toolCall = extractToolCall(assistantMessage);
                if (toolCall != null) {
                    if (isForbiddenBankingTool(toolCall.toolCode())) {
                        throw new CranialException(
                                ErrorCode.TOOL_PERMISSION_DENIED,
                                "CRANIAL is strictly a money management and business intelligence platform, NOT a banking or payment execution app. Tool '"
                                        + toolCall.toolCode() + "' is strictly forbidden."
                        );
                    }
                    ToolEntity matchedTool = availableTools.stream()
                            .filter(t -> t.getToolCode().equalsIgnoreCase(toolCall.toolCode()))
                            .findFirst()
                            .orElse(null);

                    if (matchedTool != null) {
                        // Strict Application and Role Permission Boundaries
                        if (matchedTool.getApplicationId() != null && !matchedTool.getApplicationId().equals(ctx.applicationId())) {
                            throw new CranialException(
                                    ErrorCode.TOOL_PERMISSION_DENIED,
                                    "Tool '" + matchedTool.getToolCode() + "' belongs to another application and cannot be invoked"
                            );
                        }

                        if (matchedTool.getRequiredPermission() != null && !matchedTool.getRequiredPermission().isBlank()) {
                            String req = matchedTool.getRequiredPermission();
                            boolean hasPermission = false;
                            if (!ctx.contextSigned()) {
                                if ("app:read".equals(req) || "app:write".equals(req)
                                        || (ctx.applicationCode() != null && req.startsWith(ctx.applicationCode() + ":"))) {
                                    hasPermission = true;
                                }
                            }
                            if (ctx.scopes() != null && (ctx.scopes().contains(req) || ctx.scopes().contains("*"))) {
                                hasPermission = true;
                            } else if (ctx.roles() != null && !hasPermission) {
                                hasPermission = checkRoleToolPermission(ctx.roles(), req);
                            }
                            if (!hasPermission) {
                                throw new CranialException(
                                        ErrorCode.TOOL_PERMISSION_DENIED,
                                        "Tool execution denied: User/Context lacks required permission '" + req + "' for tool '" + matchedTool.getToolCode() + "'"
                                );
                            }
                        }

                        stepStart = System.currentTimeMillis();
                        toolCallsCount++;
                        ToolExecutionResult toolRes = toolExecutor.execute(matchedTool, toolCall);
                        stepDuration = System.currentTimeMillis() - stepStart;

                        steps.add(OrchestrationStep.of(
                                ++stepIndex,
                                "Tool Invocation (" + matchedTool.getToolCode() + ")",
                                "TOOL_INVOCATION",
                                stepDuration,
                                toolRes.success() ? "SUCCESS" : "FAILURE",
                                Map.of(
                                        "tool_code", matchedTool.getToolCode(),
                                        "is_read_only", matchedTool.getIsReadOnly(),
                                        "success", toolRes.success(),
                                        "error", toolRes.errorMessage() != null ? toolRes.errorMessage() : ""
                                )
                        ));

                        if (!toolRes.success()) {
                            throw new CranialException(
                                    ErrorCode.TOOL_EXECUTION_FAILED,
                                    "Tool '" + matchedTool.getToolCode() + "' failed: " + toolRes.errorMessage()
                            );
                        }

                        // Feed tool output back to model for synthesis
                        ChatCompletionRequest toolFollowUp = new ChatCompletionRequest(
                                task.getCapabilityCode(),
                                task.getTaskCode(),
                                List.of(
                                        ChatMessage.system(systemPrompt),
                                        ChatMessage.user(userPrompt),
                                        ChatMessage.assistant(assistantMessage),
                                        ChatMessage.user("Tool output: " + objectMapper.writeValueAsString(toolRes.data()) + ". Now generate the final answer.")
                                ),
                                0.2,
                                null,
                                request.modelOverride(),
                                request.dataSensitivity(),
                                Map.of()
                        );
                        ChatCompletionResponse followUpResp = modelExecutionService.executeChat(ctx, toolFollowUp, clientIp);
                        totalPromptTokens += followUpResp.promptTokens();
                        totalCompletionTokens += followUpResp.completionTokens();
                        assistantMessage = followUpResp.message().content();
                    }
                }
            }

            // -----------------------------------------------------------------
            // Step 5: Schema Validation (Structured Model Output Guard)
            // -----------------------------------------------------------------
            Map<String, Object> structuredOutput = null;
            if (task.getOutputSchemaId() != null) {
                stepStart = System.currentTimeMillis();
                outputSchema = schemaMapper.selectById(task.getOutputSchemaId());

                if (outputSchema != null) {
                    structuredOutput = parseJsonPayload(assistantMessage);
                    SchemaValidationResult valRes = schemaValidator.validate(
                            outputSchema.getSchemaDefinitionJson(),
                            structuredOutput
                    );
                    stepDuration = System.currentTimeMillis() - stepStart;

                    steps.add(OrchestrationStep.of(
                            ++stepIndex,
                            "Schema Validation (" + outputSchema.getSchemaCode() + ")",
                            "SCHEMA_VALIDATION",
                            stepDuration,
                            valRes.valid() ? "SUCCESS" : "FAILURE",
                            Map.of(
                                    "schema_code", outputSchema.getSchemaCode(),
                                    "valid", valRes.valid(),
                                    "errors", valRes.errors()
                            )
                    ));

                    if (!valRes.valid()) {
                        String errMsg = "Model structured output failed schema validation for '" + outputSchema.getSchemaCode() + "': "
                                + String.join("; ", valRes.errors());
                        saveTrace(traceId, ctx, task, prompt, outputSchema, modelId, "SCHEMA_VALIDATION_FAILED",
                                System.currentTimeMillis() - startTime, totalPromptTokens, totalCompletionTokens, toolCallsCount, steps, errMsg);
                        throw new CranialException(ErrorCode.SCHEMA_VALIDATION_FAILED, errMsg);
                    }
                }
            }

            long totalDuration = System.currentTimeMillis() - startTime;
            saveTrace(traceId, ctx, task, prompt, outputSchema, modelId, "SUCCESS",
                    totalDuration, totalPromptTokens, totalCompletionTokens, toolCallsCount, steps, null);

            return new OrchestratorResult(
                    traceId,
                    task.getTaskCode(),
                    task.getCapabilityCode(),
                    modelName,
                    structuredOutput != null ? structuredOutput : Map.of(),
                    assistantMessage,
                    totalPromptTokens,
                    totalCompletionTokens,
                    totalPromptTokens + totalCompletionTokens,
                    totalDuration,
                    toolCallsCount,
                    steps,
                    Map.of(
                            "local_first", true,
                            "schema_validated", outputSchema != null
                    )
            );

        } catch (CranialException ce) {
            if (!ErrorCode.SCHEMA_VALIDATION_FAILED.equals(ce.errorCode())) {
                long totalDuration = System.currentTimeMillis() - startTime;
                saveTrace(traceId, ctx, task, prompt, outputSchema, modelId, "ERROR",
                        totalDuration, totalPromptTokens, totalCompletionTokens, toolCallsCount, steps, ce.getMessage());
            }
            throw ce;
        } catch (Exception ex) {
            long totalDuration = System.currentTimeMillis() - startTime;
            saveTrace(traceId, ctx, task, prompt, outputSchema, modelId, "ERROR",
                    totalDuration, totalPromptTokens, totalCompletionTokens, toolCallsCount, steps, ex.getMessage());
            throw new CranialException(ErrorCode.INTERNAL_ERROR, "Orchestration failed: " + ex.getMessage());
        }
    }

    private boolean isForbiddenBankingTool(String toolCode) {
        if (toolCode == null || toolCode.isBlank()) {
            return false;
        }
        String lower = toolCode.toLowerCase();
        return FORBIDDEN_BANKING_VERBS.stream().anyMatch(lower::contains);
    }

    private ToolExecutionRequest extractToolCall(String text) {
        try {
            Map<String, Object> json = parseJsonPayload(text);
            if (json != null) {
                String tool = null;
                if (json.containsKey("tool_call") && json.get("tool_call") != null) {
                    tool = json.get("tool_call").toString();
                } else if (json.containsKey("tool") && json.get("tool") != null) {
                    tool = json.get("tool").toString();
                }
                if (tool != null && !tool.isBlank()) {
                    Object p = json.get("parameters");
                    if (p == null) {
                        p = json.get("arguments");
                    }
                    Map<String, Object> params = p instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
                    return new ToolExecutionRequest(tool, params);
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private Map<String, Object> parseJsonPayload(String text) {
        if (text == null || text.isBlank()) {
            return Map.of();
        }
        try {
            // First try direct JSON parse
            return objectMapper.readValue(text, new TypeReference<Map<String, Object>>() {});
        } catch (Exception notDirect) {
            // Check for fenced markdown block ```json { ... } ```
            Matcher m = JSON_BLOCK_PATTERN.matcher(text);
            if (m.find()) {
                try {
                    return objectMapper.readValue(m.group(1), new TypeReference<Map<String, Object>>() {});
                } catch (Exception ignored) {
                }
            }
            // Check for first '{' and last '}'
            int start = text.indexOf('{');
            int end = text.lastIndexOf('}');
            if (start >= 0 && end > start) {
                try {
                    return objectMapper.readValue(text.substring(start, end + 1), new TypeReference<Map<String, Object>>() {});
                } catch (Exception ignored) {
                }
            }
        }
        return Map.of("raw_text", text);
    }

    private void saveTrace(
            UUID traceId,
            VerifiedRequestContext ctx,
            TaskDefinitionEntity task,
            PromptEntity prompt,
            SchemaEntity schema,
            UUID modelId,
            String status,
            long durationMs,
            int promptTokens,
            int completionTokens,
            int toolCallsCount,
            List<OrchestrationStep> steps,
            String errorMessage
    ) {
        try {
            OrchestratorTraceEntity existing = traceMapper.selectById(traceId);
            if (existing != null) {
                existing.setStatus(status);
                existing.setTotalDurationMs(durationMs);
                existing.setPromptTokens(promptTokens);
                existing.setCompletionTokens(completionTokens);
                existing.setToolInvocationsCount(toolCallsCount);
                existing.setStepsJson(objectMapper.writeValueAsString(steps));
                existing.setErrorMessage(errorMessage);
                traceMapper.updateById(existing);
                return;
            }

            OrchestratorTraceEntity entity = new OrchestratorTraceEntity();
            entity.setId(traceId);
            entity.setApplicationId(ctx.applicationId());
            entity.setTenantId(ctx.tenantId());
            entity.setCorrelationId(ctx.correlationId());
            entity.setTaskCode(task != null ? task.getTaskCode() : "unknown");
            entity.setCapabilityCode(task != null ? task.getCapabilityCode() : "unknown");
            entity.setModelId(modelId);
            entity.setPromptId(prompt != null ? prompt.getId() : null);
            entity.setSchemaId(schema != null ? schema.getId() : null);
            entity.setStatus(status);
            entity.setTotalDurationMs(durationMs);
            entity.setPromptTokens(promptTokens);
            entity.setCompletionTokens(completionTokens);
            entity.setToolInvocationsCount(toolCallsCount);
            entity.setStepsJson(objectMapper.writeValueAsString(steps));
            entity.setErrorMessage(errorMessage);
            entity.setCreatedAt(Instant.now());

            traceMapper.insert(entity);
        } catch (Exception ex) {
            log.error("Failed to persist orchestrator trace: {}", ex.getMessage());
        }
    }

    private boolean checkRoleToolPermission(List<String> roles, String requiredPermission) {
        if (roles == null || roles.isEmpty() || requiredPermission == null) {
            return false;
        }
        for (String r : roles) {
            String roleUpper = r.toUpperCase();
            if ("SUPER_ADMIN".equals(roleUpper) || "PLATFORM_OPERATOR".equals(roleUpper)) {
                return true;
            }
            if ("STORE_OWNER".equals(roleUpper)) {
                if (requiredPermission.startsWith("sms:") || "app:read".equals(requiredPermission)) {
                    return true;
                }
            }
            if ("WORKER".equals(roleUpper)) {
                if (requiredPermission.startsWith("worker:") || "app:read".equals(requiredPermission)) {
                    return true;
                }
            }
            if ("BUDGET_USER".equals(roleUpper)) {
                if (requiredPermission.startsWith("peggy:") || "app:read".equals(requiredPermission)) {
                    return true;
                }
            }
            if ("CUSTOMER".equals(roleUpper)) {
                if (requiredPermission.startsWith("ecom:") || "app:read".equals(requiredPermission)) {
                    return true;
                }
            }
            if (roleUpper.equalsIgnoreCase(requiredPermission)) {
                return true;
            }
        }
        return false;
    }
}
