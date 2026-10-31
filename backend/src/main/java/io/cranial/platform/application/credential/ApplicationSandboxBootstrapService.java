package io.cranial.platform.application.credential;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.cranial.platform.application.registry.ApplicationEntity;
import io.cranial.platform.application.registry.ApplicationMapper;
import io.cranial.platform.platform.crypto.CryptoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class ApplicationSandboxBootstrapService {

    private static final Logger log = LoggerFactory.getLogger(ApplicationSandboxBootstrapService.class);

    public record SandboxCredentialInfo(
            String applicationCode,
            String applicationName,
            String environment,
            String apiKey,
            String signingSecret,
            String tenantRef,
            String userRef,
            List<String> roles,
            List<String> scopes
    ) {}

    private static final List<SandboxDefinition> SANDBOX_SPECS = List.of(
            new SandboxDefinition(
                    "sms",
                    "Store Management System (SMS)",
                    "crn_dev_sms_app001",
                    "secret_sandbox_sms_key_9901",
                    "secret_signing_key_sandbox_sms_01",
                    "store-jakarta-01",
                    "owner@store.test",
                    List.of("STORE_OWNER"),
                    List.of("sms:owner_reports", "sms:inventory_read", "app:read")
            ),
            new SandboxDefinition(
                    "worker_app",
                    "Worker App",
                    "crn_dev_wrk_app001",
                    "secret_sandbox_wrk_key_9902",
                    "secret_signing_key_sandbox_wrk_02",
                    "store-jakarta-01",
                    "worker1@store.test",
                    List.of("WORKER"),
                    List.of("worker:tasks_read", "worker:delivery_read", "app:read")
            ),
            new SandboxDefinition(
                    "peggy",
                    "Peggy Money Management",
                    "crn_dev_pgy_app001",
                    "secret_sandbox_pgy_key_9903",
                    "secret_signing_key_sandbox_pgy_03",
                    "peggy-family-budget",
                    "user@peggy.test",
                    List.of("BUDGET_USER"),
                    List.of("peggy:budget_read", "peggy:spending_read", "app:read")
            ),
            new SandboxDefinition(
                    "ecommerce",
                    "E-Commerce Platform",
                    "crn_dev_ecm_app001",
                    "secret_sandbox_ecm_key_9904",
                    "secret_signing_key_sandbox_ecm_04",
                    "indo-market-online",
                    "buyer@shop.test",
                    List.of("CUSTOMER"),
                    List.of("ecom:catalog_read", "ecom:order_read", "app:read")
            )
    );

    private record SandboxDefinition(
            String appCode,
            String appName,
            String keyPrefix,
            String rawSecret,
            String rawSigningSecret,
            String tenantRef,
            String userRef,
            List<String> roles,
            List<String> scopes
    ) {}

    private final ApplicationMapper applicationMapper;
    private final ApplicationCredentialMapper credentialMapper;
    private final CryptoService cryptoService;

    public ApplicationSandboxBootstrapService(
            ApplicationMapper applicationMapper,
            ApplicationCredentialMapper credentialMapper,
            CryptoService cryptoService
    ) {
        this.applicationMapper = applicationMapper;
        this.credentialMapper = credentialMapper;
        this.cryptoService = cryptoService;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void bootstrapSandboxCredentials() {
        for (SandboxDefinition spec : SANDBOX_SPECS) {
            try {
                ApplicationEntity app = applicationMapper.selectOne(
                        new LambdaQueryWrapper<ApplicationEntity>()
                                .eq(ApplicationEntity::getCode, spec.appCode())
                                .eq(ApplicationEntity::getEnvironment, "DEV")
                );

                if (app == null) {
                    continue;
                }

                ApplicationCredentialEntity existing = credentialMapper.selectOne(
                        new LambdaQueryWrapper<ApplicationCredentialEntity>()
                                .eq(ApplicationCredentialEntity::getKeyPrefix, spec.keyPrefix())
                );

                if (existing == null) {
                    ApplicationCredentialEntity entity = new ApplicationCredentialEntity();
                    entity.setId(UUID.randomUUID());
                    entity.setApplicationId(app.getId());
                    entity.setName("Sandbox Key (" + spec.appCode() + ")");
                    entity.setEnvironment(app.getEnvironment());
                    entity.setKeyPrefix(spec.keyPrefix());
                    entity.setSecretHash(cryptoService.hashApiKeySecret(spec.rawSecret()));
                    entity.setSigningSecretEncrypted(cryptoService.encryptSecret(spec.rawSigningSecret()));
                    entity.setStatus("ACTIVE");
                    entity.setCreatedAt(Instant.now());

                    credentialMapper.insert(entity);
                    log.info("Bootstrapped sandbox credential for app={} keyPrefix={}", spec.appCode(), spec.keyPrefix());
                }
            } catch (Exception ex) {
                log.warn("Could not bootstrap sandbox credential for {}: {}", spec.appCode(), ex.getMessage());
            }
        }
    }

    public List<SandboxCredentialInfo> getActiveSandboxCredentials() {
        List<SandboxCredentialInfo> results = new ArrayList<>();
        for (SandboxDefinition spec : SANDBOX_SPECS) {
            String apiKey = spec.keyPrefix() + "." + spec.rawSecret();
            results.add(new SandboxCredentialInfo(
                    spec.appCode(),
                    spec.appName(),
                    "DEV",
                    apiKey,
                    spec.rawSigningSecret(),
                    spec.tenantRef(),
                    spec.userRef(),
                    spec.roles(),
                    spec.scopes()
            ));
        }
        return results;
    }

    public Optional<SandboxCredentialInfo> getSandboxCredential(String appCode) {
        if (appCode == null) return Optional.empty();
        return getActiveSandboxCredentials().stream()
                .filter(s -> s.applicationCode().equalsIgnoreCase(appCode.trim()))
                .findFirst();
    }
}

