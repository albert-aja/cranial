package io.cranial.platform.provider.service;

import io.cranial.platform.platform.exception.CranialException;
import io.cranial.platform.platform.exception.ErrorCode;
import io.cranial.platform.provider.adapter.ModelProviderAdapter;
import io.cranial.platform.provider.adapter.ProviderAdapterRegistry;
import io.cranial.platform.provider.circuit.ProviderCircuitBreaker;
import io.cranial.platform.provider.circuit.ProviderCircuitBreakerRegistry;
import io.cranial.platform.provider.entity.ModelCapabilityEntity;
import io.cranial.platform.provider.entity.ModelEntity;
import io.cranial.platform.provider.entity.ProviderEntity;
import io.cranial.platform.provider.entity.RoutingPolicyEntity;
import io.cranial.platform.provider.mapper.ModelCapabilityMapper;
import io.cranial.platform.provider.mapper.ModelMapper;
import io.cranial.platform.provider.mapper.ProviderMapper;
import io.cranial.platform.provider.mapper.RoutingPolicyMapper;
import io.cranial.platform.provider.model.ChatCompletionRequest;
import io.cranial.platform.provider.model.ChatCompletionResponse;
import io.cranial.platform.provider.model.ChatMessage;
import io.cranial.platform.provider.model.ModelView;
import io.cranial.platform.provider.model.ModelWarmupResult;
import io.cranial.platform.provider.model.ProviderHealth;
import io.cranial.platform.provider.model.ProviderView;
import io.cranial.platform.provider.model.RoutingPolicyView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class ProviderService {

    private final ProviderMapper providerMapper;
    private final ModelMapper modelMapper;
    private final ModelCapabilityMapper capabilityMapper;
    private final RoutingPolicyMapper routingPolicyMapper;
    private final ProviderAdapterRegistry adapterRegistry;
    private final ProviderCircuitBreakerRegistry circuitBreakerRegistry;

    public ProviderService(
            ProviderMapper providerMapper,
            ModelMapper modelMapper,
            ModelCapabilityMapper capabilityMapper,
            RoutingPolicyMapper routingPolicyMapper,
            ProviderAdapterRegistry adapterRegistry,
            ProviderCircuitBreakerRegistry circuitBreakerRegistry
    ) {
        this.providerMapper = providerMapper;
        this.modelMapper = modelMapper;
        this.capabilityMapper = capabilityMapper;
        this.routingPolicyMapper = routingPolicyMapper;
        this.adapterRegistry = adapterRegistry;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
    }

    // -------------------------------------------------------------------------
    // Providers
    // -------------------------------------------------------------------------

    public List<ProviderView> listProviders() {
        return providerMapper.selectList(null).stream()
                .map(this::toProviderView)
                .toList();
    }

    public ProviderView getProvider(UUID id) {
        ProviderEntity entity = providerMapper.selectById(id);
        if (entity == null) {
            throw new CranialException(ErrorCode.RESOURCE_NOT_FOUND, "Provider not found: " + id);
        }
        return toProviderView(entity);
    }

    public ProviderHealth checkProviderHealth(UUID id) {
        ProviderEntity provider = providerMapper.selectById(id);
        if (provider == null) {
            throw new CranialException(ErrorCode.RESOURCE_NOT_FOUND, "Provider not found: " + id);
        }
        if (!adapterRegistry.hasAdapter(provider.getCode())) {
            return ProviderHealth.unreachable(provider.getCode(), 0, "No runtime adapter registered for provider: " + provider.getCode());
        }
        ModelProviderAdapter adapter = adapterRegistry.getAdapter(provider.getCode());
        ProviderHealth health = adapter.checkHealth(provider);
        ProviderCircuitBreaker breaker = circuitBreakerRegistry.getOrCreate(provider.getCode());
        Map<String, Object> details = new LinkedHashMap<>(health.details());
        details.put("circuit_breaker", breaker.getDiagnostics());
        return new ProviderHealth(health.providerCode(), health.reachable(), health.latencyMs(), health.message(), details);
    }

    public void resetCircuitBreaker(UUID providerId) {
        ProviderEntity provider = providerMapper.selectById(providerId);
        if (provider == null) {
            throw new CranialException(ErrorCode.RESOURCE_NOT_FOUND, "Provider not found: " + providerId);
        }
        circuitBreakerRegistry.getOrCreate(provider.getCode()).reset();
    }

    public ModelWarmupResult warmupModel(UUID modelId) {
        ModelEntity model = modelMapper.selectById(modelId);
        if (model == null) {
            throw new CranialException(ErrorCode.MODEL_NOT_FOUND, "Model not found: " + modelId);
        }
        ProviderEntity provider = providerMapper.selectById(model.getProviderId());
        if (provider == null) {
            throw new CranialException(ErrorCode.RESOURCE_NOT_FOUND, "Provider not found for model: " + modelId);
        }

        ModelProviderAdapter adapter = adapterRegistry.getAdapter(provider.getCode());
        ChatCompletionRequest warmupReq = new ChatCompletionRequest(
                "conversational_intelligence",
                "warmup",
                List.of(new ChatMessage("user", "ping")),
                0.0,
                1,
                model.getModelIdentifier(),
                "PUBLIC",
                Map.of("warmup", true)
        );

        long start = System.currentTimeMillis();
        try {
            ChatCompletionResponse resp = adapter.chat(provider, model, warmupReq)
                    .get(Math.max(provider.getTimeoutSeconds(), 30), TimeUnit.SECONDS);
            long duration = System.currentTimeMillis() - start;
            return new ModelWarmupResult(model.getId(), model.getModelIdentifier(), true, duration, "Model warm: " + resp.model());
        } catch (Exception ex) {
            long duration = System.currentTimeMillis() - start;
            return new ModelWarmupResult(model.getId(), model.getModelIdentifier(), false, duration, "Warmup failed: " + ex.getMessage());
        }
    }

    @Transactional
    public ProviderView saveProvider(
            UUID id,
            String code,
            String name,
            String providerType,
            String baseUrl,
            String status,
            int timeoutSeconds,
            String configJson
    ) {
        ProviderEntity entity = id != null ? providerMapper.selectById(id) : null;
        boolean create = false;
        if (entity == null) {
            entity = new ProviderEntity();
            entity.setId(id != null ? id : UUID.randomUUID());
            entity.setCreatedAt(Instant.now());
            create = true;
        }

        entity.setCode(code.trim().toLowerCase());
        entity.setName(name.trim());
        entity.setProviderType(providerType.toUpperCase());
        entity.setBaseUrl(baseUrl.trim());
        entity.setStatus(status != null ? status.toUpperCase() : "ACTIVE");
        entity.setTimeoutSeconds(timeoutSeconds > 0 ? timeoutSeconds : 30);
        entity.setConfigJson(configJson != null ? configJson : "{}");
        entity.setUpdatedAt(Instant.now());

        if (create) {
            providerMapper.insert(entity);
        } else {
            providerMapper.updateById(entity);
        }

        return toProviderView(entity);
    }

    // -------------------------------------------------------------------------
    // Models
    // -------------------------------------------------------------------------

    public List<ModelView> listModels() {
        Map<UUID, ProviderEntity> providerMap = providerMapper.selectList(null).stream()
                .collect(java.util.stream.Collectors.toMap(ProviderEntity::getId, p -> p));

        return modelMapper.selectList(null).stream()
                .map(m -> {
                    ProviderEntity p = providerMap.get(m.getProviderId());
                    List<String> caps = capabilityMapper.listByModelId(m.getId());
                    return toModelView(m, p, caps);
                })
                .toList();
    }

    @Transactional
    public ModelView saveModel(
            UUID id,
            UUID providerId,
            String modelIdentifier,
            String displayName,
            String modality,
            int contextWindowTokens,
            int maxOutputTokens,
            int costPerMillionInputCents,
            int costPerMillionOutputCents,
            boolean isLocal,
            String status,
            List<String> capabilities,
            String metadataJson
    ) {
        ProviderEntity provider = providerMapper.selectById(providerId);
        if (provider == null) {
            throw new CranialException(ErrorCode.RESOURCE_NOT_FOUND, "Provider not found: " + providerId);
        }

        ModelEntity entity = id != null ? modelMapper.selectById(id) : null;
        boolean create = false;
        if (entity == null) {
            entity = new ModelEntity();
            entity.setId(id != null ? id : UUID.randomUUID());
            entity.setCreatedAt(Instant.now());
            create = true;
        }

        entity.setProviderId(providerId);
        entity.setModelIdentifier(modelIdentifier.trim());
        entity.setDisplayName(displayName.trim());
        entity.setModality(modality.toUpperCase());
        entity.setContextWindowTokens(contextWindowTokens > 0 ? contextWindowTokens : 8192);
        entity.setMaxOutputTokens(Math.max(0, maxOutputTokens));
        entity.setCostPerMillionInputCents(Math.max(0, costPerMillionInputCents));
        entity.setCostPerMillionOutputCents(Math.max(0, costPerMillionOutputCents));
        entity.setIsLocal(isLocal);
        entity.setStatus(status != null ? status.toUpperCase() : "ACTIVE");
        entity.setMetadataJson(metadataJson != null ? metadataJson : "{}");
        entity.setUpdatedAt(Instant.now());

        if (create) {
            modelMapper.insert(entity);
        } else {
            modelMapper.updateById(entity);
        }

        if (capabilities != null) {
            capabilityMapper.deleteByModelId(entity.getId());
            for (String cap : capabilities) {
                if (cap != null && !cap.isBlank()) {
                    capabilityMapper.insert(new ModelCapabilityEntity(entity.getId(), cap.trim()));
                }
            }
        }

        List<String> savedCaps = capabilityMapper.listByModelId(entity.getId());
        return toModelView(entity, provider, savedCaps);
    }

    // -------------------------------------------------------------------------
    // Routing Policies
    // -------------------------------------------------------------------------

    public List<RoutingPolicyView> listRoutingPolicies() {
        Map<UUID, ModelEntity> modelMap = modelMapper.selectList(null).stream()
                .collect(java.util.stream.Collectors.toMap(ModelEntity::getId, m -> m));

        return routingPolicyMapper.selectList(null).stream()
                .map(p -> {
                    ModelEntity pref = modelMap.get(p.getPreferredModelId());
                    ModelEntity fall = p.getFallbackModelId() != null ? modelMap.get(p.getFallbackModelId()) : null;
                    return new RoutingPolicyView(
                            p.getId(),
                            p.getName(),
                            p.getCapabilityCode(),
                            p.getTaskCode(),
                            p.getPreferredModelId(),
                            pref != null ? pref.getModelIdentifier() : "unknown",
                            pref != null && Boolean.TRUE.equals(pref.getIsLocal()),
                            p.getFallbackModelId(),
                            fall != null ? fall.getModelIdentifier() : null,
                            fall != null && Boolean.TRUE.equals(fall.getIsLocal()),
                            p.getAllowedDataSensitivity(),
                            Boolean.TRUE.equals(p.getIsActive()),
                            p.getCreatedAt(),
                            p.getUpdatedAt()
                    );
                })
                .toList();
    }

    @Transactional
    public RoutingPolicyView saveRoutingPolicy(
            UUID id,
            String name,
            String capabilityCode,
            String taskCode,
            UUID preferredModelId,
            UUID fallbackModelId,
            String allowedDataSensitivity,
            boolean isActive
    ) {
        ModelEntity pref = modelMapper.selectById(preferredModelId);
        if (pref == null) {
            throw new CranialException(ErrorCode.RESOURCE_NOT_FOUND, "Preferred model not found: " + preferredModelId);
        }

        ModelEntity fall = null;
        if (fallbackModelId != null) {
            fall = modelMapper.selectById(fallbackModelId);
            if (fall == null) {
                throw new CranialException(ErrorCode.RESOURCE_NOT_FOUND, "Fallback model not found: " + fallbackModelId);
            }
        }

        RoutingPolicyEntity entity = id != null ? routingPolicyMapper.selectById(id) : null;
        boolean create = false;
        if (entity == null) {
            entity = new RoutingPolicyEntity();
            entity.setId(id != null ? id : UUID.randomUUID());
            entity.setCreatedAt(Instant.now());
            create = true;
        }

        entity.setName(name.trim());
        entity.setCapabilityCode(capabilityCode.trim());
        entity.setTaskCode(taskCode != null && !taskCode.isBlank() ? taskCode.trim() : null);
        entity.setPreferredModelId(preferredModelId);
        entity.setFallbackModelId(fallbackModelId);
        entity.setAllowedDataSensitivity(allowedDataSensitivity != null ? allowedDataSensitivity.toUpperCase() : "RESTRICTED");
        entity.setIsActive(isActive);
        entity.setUpdatedAt(Instant.now());

        if (create) {
            routingPolicyMapper.insert(entity);
        } else {
            routingPolicyMapper.updateById(entity);
        }

        return new RoutingPolicyView(
                entity.getId(),
                entity.getName(),
                entity.getCapabilityCode(),
                entity.getTaskCode(),
                entity.getPreferredModelId(),
                pref.getModelIdentifier(),
                Boolean.TRUE.equals(pref.getIsLocal()),
                entity.getFallbackModelId(),
                fall != null ? fall.getModelIdentifier() : null,
                fall != null && Boolean.TRUE.equals(fall.getIsLocal()),
                entity.getAllowedDataSensitivity(),
                Boolean.TRUE.equals(entity.getIsActive()),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private ProviderView toProviderView(ProviderEntity p) {
        return new ProviderView(
                p.getId(),
                p.getCode(),
                p.getName(),
                p.getProviderType(),
                p.getBaseUrl(),
                p.getStatus(),
                p.getTimeoutSeconds() != null ? p.getTimeoutSeconds() : 30,
                p.getApiKeyEncrypted() != null && !p.getApiKeyEncrypted().isBlank(),
                p.getConfigJson(),
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }

    private ModelView toModelView(ModelEntity m, ProviderEntity p, List<String> caps) {
        return new ModelView(
                m.getId(),
                m.getProviderId(),
                p != null ? p.getCode() : "unknown",
                p != null ? p.getName() : "unknown",
                m.getModelIdentifier(),
                m.getDisplayName(),
                m.getModality(),
                m.getContextWindowTokens() != null ? m.getContextWindowTokens() : 8192,
                m.getMaxOutputTokens() != null ? m.getMaxOutputTokens() : 2048,
                m.getCostPerMillionInputCents() != null ? m.getCostPerMillionInputCents() : 0,
                m.getCostPerMillionOutputCents() != null ? m.getCostPerMillionOutputCents() : 0,
                Boolean.TRUE.equals(m.getIsLocal()),
                m.getStatus(),
                caps != null ? caps : List.of(),
                m.getMetadataJson(),
                m.getCreatedAt(),
                m.getUpdatedAt()
        );
    }
}

