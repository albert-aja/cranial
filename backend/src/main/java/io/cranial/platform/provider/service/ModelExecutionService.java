package io.cranial.platform.provider.service;

import io.cranial.platform.audit.AuditEvent;
import io.cranial.platform.audit.AuditService;
import io.cranial.platform.platform.exception.CranialException;
import io.cranial.platform.platform.exception.ErrorCode;
import io.cranial.platform.provider.adapter.ModelProviderAdapter;
import io.cranial.platform.provider.adapter.ProviderAdapterRegistry;
import io.cranial.platform.provider.circuit.ProviderCircuitBreaker;
import io.cranial.platform.provider.circuit.ProviderCircuitBreakerRegistry;
import io.cranial.platform.provider.entity.ModelEntity;
import io.cranial.platform.provider.entity.ProviderEntity;
import io.cranial.platform.provider.model.ChatCompletionRequest;
import io.cranial.platform.provider.model.ChatCompletionResponse;
import io.cranial.platform.provider.model.ChatMessage;
import io.cranial.platform.provider.model.EmbeddingRequest;
import io.cranial.platform.provider.model.EmbeddingResponse;
import io.cranial.platform.provider.model.RoutingDecision;
import io.cranial.platform.provider.routing.ModelRouter;
import io.cranial.platform.security.VerifiedRequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class ModelExecutionService {

    private static final Logger log = LoggerFactory.getLogger(ModelExecutionService.class);

    private final ModelRouter modelRouter;
    private final ProviderAdapterRegistry adapterRegistry;
    private final UsageTrackingService usageTrackingService;
    private final AuditService auditService;
    private final ContextPruner contextPruner;
    private final ProviderCircuitBreakerRegistry circuitBreakerRegistry;

    public ModelExecutionService(
            ModelRouter modelRouter,
            ProviderAdapterRegistry adapterRegistry,
            UsageTrackingService usageTrackingService,
            AuditService auditService,
            ContextPruner contextPruner,
            ProviderCircuitBreakerRegistry circuitBreakerRegistry
    ) {
        this.modelRouter = modelRouter;
        this.adapterRegistry = adapterRegistry;
        this.usageTrackingService = usageTrackingService;
        this.auditService = auditService;
        this.contextPruner = contextPruner;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
    }

    public ChatCompletionResponse executeChat(
            VerifiedRequestContext ctx,
            ChatCompletionRequest request,
            String clientIp
    ) {
        RoutingDecision decision = modelRouter.route(
                ctx.applicationId(),
                ctx.allowHostedProviders(),
                ctx.allowSensitiveExternal(),
                request.capability(),
                request.task(),
                request.dataSensitivity(),
                request.modelOverride()
        );

        long startTime = System.currentTimeMillis();
        ModelEntity targetModel = decision.model();
        ProviderEntity targetProvider = decision.provider();
        boolean fallbackEngaged = false;
        ChatCompletionResponse response = null;

        List<ChatMessage> prunedMessages = contextPruner.pruneMessages(targetModel, request.messages(), request.maxTokens());
        ChatCompletionRequest effectiveRequest = new ChatCompletionRequest(
                request.capability(),
                request.task(),
                prunedMessages,
                request.temperature(),
                request.maxTokens(),
                request.modelOverride(),
                request.dataSensitivity(),
                request.context()
        );

        ProviderCircuitBreaker primaryBreaker = circuitBreakerRegistry.getOrCreate(targetProvider.getCode());

        try {
            if (!primaryBreaker.canExecute()) {
                throw new CranialException(
                        ErrorCode.PROVIDER_CIRCUIT_OPEN,
                        "Circuit breaker is OPEN for provider: " + targetProvider.getCode() + ". Failing fast."
                );
            }

            ModelProviderAdapter primaryAdapter = adapterRegistry.getAdapter(targetProvider.getCode());
            response = primaryAdapter.chat(targetProvider, targetModel, effectiveRequest)
                    .get(targetProvider.getTimeoutSeconds(), TimeUnit.SECONDS);
            primaryBreaker.recordSuccess();

        } catch (Exception primaryEx) {
            primaryBreaker.recordFailure();
            log.warn("Primary model {} execution failed: {}", targetModel.getModelIdentifier(), primaryEx.getMessage());

            // Check if fallback model exists and is permitted
            ModelEntity fallbackModel = decision.fallbackModel();
            ProviderEntity fallbackProvider = decision.fallbackProvider();

            if (fallbackModel == null) {
                long duration = System.currentTimeMillis() - startTime;
                usageTrackingService.recordUsage(
                        ctx.applicationId(),
                        ctx.tenantId(),
                        ctx.correlationId(),
                        targetProvider,
                        targetModel,
                        request.capability(),
                        request.task(),
                        0,
                        0,
                        duration,
                        "ERROR",
                        primaryEx.getMessage()
                );
                throw translateException(primaryEx, "Primary model execution failed: " + targetModel.getModelIdentifier());
            }

            boolean fallbackPermitted = modelRouter.isFallbackPermitted(
                    fallbackModel,
                    ctx.allowHostedProviders(),
                    ctx.allowSensitiveExternal(),
                    request.dataSensitivity()
            );

            if (!fallbackPermitted) {
                long duration = System.currentTimeMillis() - startTime;
                usageTrackingService.recordUsage(
                        ctx.applicationId(),
                        ctx.tenantId(),
                        ctx.correlationId(),
                        fallbackProvider != null ? fallbackProvider : targetProvider,
                        fallbackModel,
                        request.capability(),
                        request.task(),
                        0,
                        0,
                        duration,
                        "FALLBACK_BLOCKED",
                        "Primary failed. Fallback to external model blocked by local-first policy."
                );

                auditService.record(new AuditEvent(
                        "APPLICATION",
                        ctx.applicationCode(),
                        ctx.applicationId(),
                        ctx.tenantId(),
                        "PAID_FALLBACK_BLOCKED",
                        "ROUTER",
                        fallbackModel.getModelIdentifier(),
                        "DENIED",
                        Map.of(
                                "preferred_model", targetModel.getModelIdentifier(),
                                "fallback_model", fallbackModel.getModelIdentifier(),
                                "allow_hosted_providers", ctx.allowHostedProviders()
                        ),
                        clientIp
                ));

                throw new CranialException(
                        ErrorCode.FALLBACK_BLOCKED,
                        "Primary local model '" + targetModel.getModelIdentifier() + "' failed and fallback to '"
                                + fallbackModel.getModelIdentifier() + "' was blocked by strict local-first governance policy."
                );
            }

            // Fallback is permitted (e.g. another local model or explicitly allowed hosted model)
            try {
                targetModel = fallbackModel;
                targetProvider = fallbackProvider;
                fallbackEngaged = true;

                ProviderCircuitBreaker fallbackBreaker = circuitBreakerRegistry.getOrCreate(fallbackProvider.getCode());
                if (!fallbackBreaker.canExecute()) {
                    throw new CranialException(
                            ErrorCode.PROVIDER_CIRCUIT_OPEN,
                            "Circuit breaker is OPEN for fallback provider: " + fallbackProvider.getCode()
                    );
                }

                ModelProviderAdapter fallbackAdapter = adapterRegistry.getAdapter(fallbackProvider.getCode());
                response = fallbackAdapter.chat(fallbackProvider, fallbackModel, effectiveRequest)
                        .get(fallbackProvider.getTimeoutSeconds(), TimeUnit.SECONDS);
                fallbackBreaker.recordSuccess();

            } catch (Exception fallbackEx) {
                if (fallbackProvider != null) {
                    circuitBreakerRegistry.getOrCreate(fallbackProvider.getCode()).recordFailure();
                }
                long duration = System.currentTimeMillis() - startTime;
                usageTrackingService.recordUsage(
                        ctx.applicationId(),
                        ctx.tenantId(),
                        ctx.correlationId(),
                        targetProvider,
                        targetModel,
                        request.capability(),
                        request.task(),
                        0,
                        0,
                        duration,
                        "ERROR",
                        fallbackEx.getMessage()
                );
                throw translateException(fallbackEx, "Fallback model execution failed: " + targetModel.getModelIdentifier());
            }
        }

        long totalDuration = System.currentTimeMillis() - startTime;
        usageTrackingService.recordUsage(
                ctx.applicationId(),
                ctx.tenantId(),
                ctx.correlationId(),
                targetProvider,
                targetModel,
                request.capability(),
                request.task(),
                response.promptTokens(),
                response.completionTokens(),
                totalDuration,
                "SUCCESS",
                null
        );

        if (fallbackEngaged) {
            response = new ChatCompletionResponse(
                    response.id(),
                    response.model(),
                    response.provider(),
                    response.message(),
                    response.promptTokens(),
                    response.completionTokens(),
                    response.totalTokens(),
                    totalDuration,
                    response.isLocal(),
                    true,
                    response.metadata()
            );
        }

        return response;
    }

    public EmbeddingResponse executeEmbed(
            VerifiedRequestContext ctx,
            EmbeddingRequest request,
            String clientIp
    ) {
        RoutingDecision decision = modelRouter.route(
                ctx.applicationId(),
                ctx.allowHostedProviders(),
                ctx.allowSensitiveExternal(),
                request.capability(),
                null,
                request.dataSensitivity(),
                request.modelOverride()
        );

        long startTime = System.currentTimeMillis();
        ModelEntity targetModel = decision.model();
        ProviderEntity targetProvider = decision.provider();

        try {
            ModelProviderAdapter primaryAdapter = adapterRegistry.getAdapter(targetProvider.getCode());
            EmbeddingResponse response = primaryAdapter.embed(targetProvider, targetModel, request)
                    .get(targetProvider.getTimeoutSeconds(), TimeUnit.SECONDS);

            long totalDuration = System.currentTimeMillis() - startTime;
            usageTrackingService.recordUsage(
                    ctx.applicationId(),
                    ctx.tenantId(),
                    ctx.correlationId(),
                    targetProvider,
                    targetModel,
                    request.capability(),
                    "embed",
                    response.promptTokens(),
                    0,
                    totalDuration,
                    "SUCCESS",
                    null
            );

            return response;
        } catch (Exception ex) {
            long duration = System.currentTimeMillis() - startTime;
            usageTrackingService.recordUsage(
                    ctx.applicationId(),
                    ctx.tenantId(),
                    ctx.correlationId(),
                    targetProvider,
                    targetModel,
                    request.capability(),
                    "embed",
                    0,
                    0,
                    duration,
                    "ERROR",
                    ex.getMessage()
            );
            throw translateException(ex, "Embedding execution failed: " + targetModel.getModelIdentifier());
        }
    }

    public SseEmitter executeChatStream(
            VerifiedRequestContext ctx,
            ChatCompletionRequest request,
            String clientIp
    ) {
        RoutingDecision decision = modelRouter.route(
                ctx.applicationId(),
                ctx.allowHostedProviders(),
                ctx.allowSensitiveExternal(),
                request.capability(),
                request.task(),
                request.dataSensitivity(),
                request.modelOverride()
        );

        ModelEntity targetModel = decision.model();
        ProviderEntity targetProvider = decision.provider();

        ProviderCircuitBreaker breaker = circuitBreakerRegistry.getOrCreate(targetProvider.getCode());
        if (!breaker.canExecute()) {
            throw new CranialException(
                    ErrorCode.PROVIDER_CIRCUIT_OPEN,
                    "Circuit breaker is OPEN for provider '" + targetProvider.getCode() + "'. Local runtime is undergoing cool-down."
            );
        }

        List<ChatMessage> prunedMessages = contextPruner.pruneMessages(targetModel, request.messages(), request.maxTokens());
        ChatCompletionRequest effectiveRequest = new ChatCompletionRequest(
                request.capability(),
                request.task(),
                prunedMessages,
                request.temperature(),
                request.maxTokens(),
                request.modelOverride(),
                request.dataSensitivity(),
                request.context()
        );

        SseEmitter emitter = new SseEmitter(60_000L);
        long startTime = System.currentTimeMillis();

        try {
            ModelProviderAdapter adapter = adapterRegistry.getAdapter(targetProvider.getCode());
            adapter.streamChat(
                    targetProvider,
                    targetModel,
                    effectiveRequest,
                    chunk -> {
                        try {
                            emitter.send(SseEmitter.event()
                                    .name("chunk")
                                    .data(Map.of("delta", chunk)));
                        } catch (Exception ex) {
                            log.debug("Error delivering SSE chunk: {}", ex.getMessage());
                        }
                    },
                    finalResp -> {
                        breaker.recordSuccess();
                        long duration = System.currentTimeMillis() - startTime;
                        usageTrackingService.recordUsage(
                                ctx.applicationId(),
                                ctx.tenantId(),
                                ctx.correlationId(),
                                targetProvider,
                                targetModel,
                                request.capability(),
                                request.task(),
                                finalResp.promptTokens(),
                                finalResp.completionTokens(),
                                duration,
                                "SUCCESS",
                                null
                        );
                        try {
                            emitter.send(SseEmitter.event()
                                    .name("done")
                                    .data(Map.of(
                                            "id", finalResp.id(),
                                            "model", finalResp.model(),
                                            "prompt_tokens", finalResp.promptTokens(),
                                            "completion_tokens", finalResp.completionTokens(),
                                            "total_tokens", finalResp.totalTokens(),
                                            "duration_ms", duration
                                    )));
                            emitter.complete();
                        } catch (Exception ex) {
                            emitter.completeWithError(ex);
                        }
                    }
            ).exceptionally(ex -> {
                breaker.recordFailure();
                long duration = System.currentTimeMillis() - startTime;
                usageTrackingService.recordUsage(
                        ctx.applicationId(),
                        ctx.tenantId(),
                        ctx.correlationId(),
                        targetProvider,
                        targetModel,
                        request.capability(),
                        request.task(),
                        0,
                        0,
                        duration,
                        "ERROR",
                        ex.getMessage()
                );
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data(Map.of(
                                    "code", "STREAM_ERROR",
                                    "message", ex.getMessage() != null ? ex.getMessage() : "Unknown error"
                            )));
                    emitter.completeWithError(ex);
                } catch (Exception ignored) {
                }
                return null;
            });
        } catch (Exception ex) {
            breaker.recordFailure();
            emitter.completeWithError(ex);
        }

        return emitter;
    }

    private CranialException translateException(Throwable ex, String defaultMessage) {
        Throwable cause = ex instanceof ExecutionException ? ex.getCause() : ex;
        if (cause instanceof CranialException ce) {
            return ce;
        }
        if (cause instanceof TimeoutException) {
            return new CranialException(ErrorCode.PROVIDER_TIMEOUT, "Provider invocation timed out");
        }
        return new CranialException(ErrorCode.PROVIDER_UNAVAILABLE, defaultMessage + ": " + cause.getMessage());
    }
}

