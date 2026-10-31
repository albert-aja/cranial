package io.cranial.platform.provider.routing;

import io.cranial.platform.application.registry.ApplicationEntity;
import io.cranial.platform.application.registry.ApplicationMapper;
import io.cranial.platform.application.registry.ApplicationSettingsEntity;
import io.cranial.platform.application.registry.ApplicationSettingsMapper;
import io.cranial.platform.platform.exception.CranialException;
import io.cranial.platform.platform.exception.ErrorCode;
import io.cranial.platform.provider.entity.ModelEntity;
import io.cranial.platform.provider.entity.ProviderEntity;
import io.cranial.platform.provider.entity.RoutingPolicyEntity;
import io.cranial.platform.provider.mapper.ModelMapper;
import io.cranial.platform.provider.mapper.ProviderMapper;
import io.cranial.platform.provider.mapper.RoutingPolicyMapper;
import io.cranial.platform.provider.model.RoutingDecision;
import io.cranial.platform.provider.model.SimulationRequest;
import io.cranial.platform.provider.model.SimulationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class ModelRouter {

    private static final Logger log = LoggerFactory.getLogger(ModelRouter.class);

    private final RoutingPolicyMapper routingPolicyMapper;
    private final ModelMapper modelMapper;
    private final ProviderMapper providerMapper;
    private final ApplicationMapper applicationMapper;
    private final ApplicationSettingsMapper settingsMapper;

    public ModelRouter(
            RoutingPolicyMapper routingPolicyMapper,
            ModelMapper modelMapper,
            ProviderMapper providerMapper,
            ApplicationMapper applicationMapper,
            ApplicationSettingsMapper settingsMapper
    ) {
        this.routingPolicyMapper = routingPolicyMapper;
        this.modelMapper = modelMapper;
        this.providerMapper = providerMapper;
        this.applicationMapper = applicationMapper;
        this.settingsMapper = settingsMapper;
    }

    public RoutingDecision route(
            UUID applicationId,
            boolean allowHostedProviders,
            boolean allowSensitiveExternal,
            String capabilityCode,
            String taskCode,
            String dataSensitivity,
            String modelOverride
    ) {
        String sensitivity = normalizeSensitivity(dataSensitivity);

        // 1. Model override handling
        if (modelOverride != null && !modelOverride.isBlank()) {
            ModelEntity overrideModel = findModelByIdentifier(modelOverride)
                    .orElseThrow(() -> new CranialException(
                            ErrorCode.MODEL_NOT_FOUND,
                            "Requested model override not found: " + modelOverride
                    ));

            if (!"ACTIVE".equalsIgnoreCase(overrideModel.getStatus())) {
                throw new CranialException(ErrorCode.MODEL_NOT_FOUND, "Model is not active: " + modelOverride);
            }

            // Capability validation
            List<String> caps = modelMapper.findCapabilitiesByModelId(overrideModel.getId());
            if (!caps.contains(capabilityCode)) {
                throw new CranialException(
                        ErrorCode.FORBIDDEN,
                        "Model '" + modelOverride + "' does not support capability: " + capabilityCode
                );
            }

            // Governance on override
            validateGovernance(overrideModel, allowHostedProviders, allowSensitiveExternal, sensitivity);
            validateApplicationAllowlist(applicationId, overrideModel.getModelIdentifier());

            ProviderEntity provider = providerMapper.selectById(overrideModel.getProviderId());
            return new RoutingDecision(
                    overrideModel,
                    provider,
                    null,
                    null,
                    "Manual Override (" + modelOverride + ")",
                    false,
                    "Explicit client override"
            );
        }

        // 2. Policy Lookup
        RoutingPolicyEntity policy = findMatchingPolicy(capabilityCode, taskCode)
                .orElseGet(() -> fallbackToAnyActiveLocalModel(capabilityCode));

        ModelEntity preferredModel = modelMapper.selectById(policy.getPreferredModelId());
        if (preferredModel == null || !"ACTIVE".equalsIgnoreCase(preferredModel.getStatus())) {
            throw new CranialException(
                    ErrorCode.MODEL_NOT_FOUND,
                    "Preferred model configured in policy '" + policy.getName() + "' is not active or available"
            );
        }

        validateGovernance(preferredModel, allowHostedProviders, allowSensitiveExternal, sensitivity);
        validateApplicationAllowlist(applicationId, preferredModel.getModelIdentifier());

        ProviderEntity preferredProvider = providerMapper.selectById(preferredModel.getProviderId());

        ModelEntity fallbackModel = null;
        ProviderEntity fallbackProvider = null;
        if (policy.getFallbackModelId() != null) {
            fallbackModel = modelMapper.selectById(policy.getFallbackModelId());
            if (fallbackModel != null && "ACTIVE".equalsIgnoreCase(fallbackModel.getStatus())) {
                fallbackProvider = providerMapper.selectById(fallbackModel.getProviderId());
            }
        }

        return new RoutingDecision(
                preferredModel,
                preferredProvider,
                fallbackModel,
                fallbackProvider,
                policy.getName(),
                false,
                "Policy-matched route: " + policy.getName()
        );
    }

    public void validateGovernance(
            ModelEntity model,
            boolean allowHostedProviders,
            boolean allowSensitiveExternal,
            String dataSensitivity
    ) {
        if (!Boolean.TRUE.equals(model.getIsLocal())) {
            if (!allowHostedProviders) {
                throw new CranialException(
                        ErrorCode.HOSTED_PROVIDER_BLOCKED,
                        "Application settings prohibit routing to external/hosted AI providers. Local models only."
                );
            }
            if ("RESTRICTED".equalsIgnoreCase(dataSensitivity)) {
                throw new CranialException(
                        ErrorCode.SENSITIVE_DATA_BLOCKED,
                        "Data sensitivity 'RESTRICTED' cannot be dispatched to external or hosted providers."
                );
            }
            if (!allowSensitiveExternal && !"PUBLIC".equalsIgnoreCase(dataSensitivity)) {
                throw new CranialException(
                        ErrorCode.SENSITIVE_DATA_BLOCKED,
                        "Application settings require local-first privacy. Internal/Restricted data blocked from hosted AI."
                );
            }
        }
    }

    private void validateApplicationAllowlist(UUID applicationId, String modelIdentifier) {
        if (applicationId == null) {
            return;
        }
        ApplicationSettingsEntity settings = settingsMapper.selectById(applicationId);
        if (settings == null || settings.getConfigJson() == null || settings.getConfigJson().isBlank()) {
            return;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(settings.getConfigJson());
            if (root.has("allowed_models") && root.get("allowed_models").isArray()) {
                boolean allowed = false;
                for (com.fasterxml.jackson.databind.JsonNode m : root.get("allowed_models")) {
                    if (m.asText().equalsIgnoreCase(modelIdentifier)) {
                        allowed = true;
                        break;
                    }
                }
                if (!allowed) {
                    throw new CranialException(
                            ErrorCode.MODEL_NOT_ALLOWED,
                            "Model '" + modelIdentifier + "' is not permitted in the application's model allowlist"
                    );
                }
            }
        } catch (CranialException ce) {
            throw ce;
        } catch (Exception ignored) {
        }
    }

    public boolean isFallbackPermitted(
            ModelEntity fallbackModel,
            boolean allowHostedProviders,
            boolean allowSensitiveExternal,
            String dataSensitivity
    ) {
        if (fallbackModel == null || !"ACTIVE".equalsIgnoreCase(fallbackModel.getStatus())) {
            return false;
        }
        if (!Boolean.TRUE.equals(fallbackModel.getIsLocal())) {
            // Strict No-Paid-Fallback: if application doesn't permit hosted providers, fallback is strictly blocked
            if (!allowHostedProviders) {
                return false;
            }
            if ("RESTRICTED".equalsIgnoreCase(dataSensitivity)) {
                return false;
            }
            if (!allowSensitiveExternal && !"PUBLIC".equalsIgnoreCase(dataSensitivity)) {
                return false;
            }
        }
        return true;
    }

    public SimulationResult simulateRouting(SimulationRequest req) {
        ApplicationEntity app = applicationMapper.findByCode(req.applicationCode().trim().toLowerCase())
                .orElse(null);
        if (app == null) {
            return SimulationResult.blocked(
                    req.applicationCode(),
                    req.capabilityCode(),
                    req.taskCode(),
                    req.dataSensitivity(),
                    "N/A",
                    "Application not found: " + req.applicationCode()
            );
        }

        ApplicationSettingsEntity settings = settingsMapper.selectById(app.getId());
        boolean allowHosted = settings != null && Boolean.TRUE.equals(settings.getAllowHostedProviders());
        boolean allowSensitive = settings != null && Boolean.TRUE.equals(settings.getAllowSensitiveExternal());
        String sensitivity = normalizeSensitivity(req.dataSensitivity());

        Optional<RoutingPolicyEntity> policyOpt = findMatchingPolicy(req.capabilityCode(), req.taskCode());
        if (policyOpt.isEmpty()) {
            List<ModelEntity> activeModels = modelMapper.findActiveByCapability(req.capabilityCode());
            if (activeModels.isEmpty()) {
                return SimulationResult.blocked(
                        req.applicationCode(),
                        req.capabilityCode(),
                        req.taskCode(),
                        sensitivity,
                        "N/A",
                        "No active models or routing policies found for capability: " + req.capabilityCode()
                );
            }
            ModelEntity first = activeModels.get(0);
            return SimulationResult.success(
                    req.applicationCode(),
                    req.capabilityCode(),
                    req.taskCode(),
                    sensitivity,
                    "Dynamic Capability Match",
                    first.getId(),
                    first.getModelIdentifier(),
                    Boolean.TRUE.equals(first.getIsLocal()),
                    null,
                    null,
                    false,
                    false,
                    "Resolved to active local model via dynamic capability match"
            );
        }

        RoutingPolicyEntity policy = policyOpt.get();
        ModelEntity preferred = modelMapper.selectById(policy.getPreferredModelId());
        if (preferred == null) {
            return SimulationResult.blocked(
                    req.applicationCode(),
                    req.capabilityCode(),
                    req.taskCode(),
                    sensitivity,
                    policy.getName(),
                    "Configured preferred model missing"
            );
        }

        // Check if preferred model is blocked
        if (!Boolean.TRUE.equals(preferred.getIsLocal())) {
            if (!allowHosted) {
                return SimulationResult.blocked(
                        req.applicationCode(),
                        req.capabilityCode(),
                        req.taskCode(),
                        sensitivity,
                        policy.getName(),
                        "Blocked: Application does not allow hosted providers (allow_hosted_providers=false)"
                );
            }
            if ("RESTRICTED".equalsIgnoreCase(sensitivity)) {
                return SimulationResult.blocked(
                        req.applicationCode(),
                        req.capabilityCode(),
                        req.taskCode(),
                        sensitivity,
                        policy.getName(),
                        "Blocked: Restricted data sensitivity prohibited on hosted provider"
                );
            }
        }

        ModelEntity fallback = null;
        boolean fallbackAllowed = false;
        if (policy.getFallbackModelId() != null) {
            fallback = modelMapper.selectById(policy.getFallbackModelId());
            if (fallback != null) {
                fallbackAllowed = isFallbackPermitted(fallback, allowHosted, allowSensitive, sensitivity);
            }
        }

        return SimulationResult.success(
                req.applicationCode(),
                req.capabilityCode(),
                req.taskCode(),
                sensitivity,
                policy.getName(),
                preferred.getId(),
                preferred.getModelIdentifier(),
                Boolean.TRUE.equals(preferred.getIsLocal()),
                fallback != null ? fallback.getId() : null,
                fallback != null ? fallback.getModelIdentifier() : null,
                fallback != null && Boolean.TRUE.equals(fallback.getIsLocal()),
                fallbackAllowed,
                "Route resolved successfully. Local-first policy respected."
        );
    }

    private Optional<RoutingPolicyEntity> findMatchingPolicy(String capabilityCode, String taskCode) {
        if (taskCode != null && !taskCode.isBlank()) {
            Optional<RoutingPolicyEntity> specific = routingPolicyMapper.findSpecificPolicy(capabilityCode, taskCode.trim());
            if (specific.isPresent()) {
                return specific;
            }
        }
        return routingPolicyMapper.findDefaultPolicy(capabilityCode);
    }

    private RoutingPolicyEntity fallbackToAnyActiveLocalModel(String capabilityCode) {
        List<ModelEntity> active = modelMapper.findActiveByCapability(capabilityCode);
        if (active.isEmpty()) {
            throw new CranialException(
                    ErrorCode.ROUTING_POLICY_NOT_FOUND,
                    "No active routing policy or model available for capability: " + capabilityCode
            );
        }
        ModelEntity first = active.get(0);
        RoutingPolicyEntity synthetic = new RoutingPolicyEntity();
        synthetic.setName("Dynamic Local Route (" + capabilityCode + ")");
        synthetic.setCapabilityCode(capabilityCode);
        synthetic.setPreferredModelId(first.getId());
        synthetic.setAllowedDataSensitivity("RESTRICTED");
        synthetic.setIsActive(true);
        return synthetic;
    }

    private Optional<ModelEntity> findModelByIdentifier(String identifier) {
        return modelMapper.selectList(null).stream()
                .filter(m -> m.getModelIdentifier().equalsIgnoreCase(identifier.trim()))
                .findFirst();
    }

    private String normalizeSensitivity(String raw) {
        if (raw == null || raw.isBlank()) {
            return "INTERNAL";
        }
        String upper = raw.trim().toUpperCase();
        if ("PUBLIC".equals(upper) || "INTERNAL".equals(upper) || "RESTRICTED".equals(upper)) {
            return upper;
        }
        return "INTERNAL";
    }
}
