package io.cranial.platform.provider.service;

import io.cranial.platform.provider.entity.ModelEntity;
import io.cranial.platform.provider.entity.ProviderEntity;
import io.cranial.platform.provider.entity.UsageRecordEntity;
import io.cranial.platform.provider.mapper.UsageRecordMapper;
import io.cranial.platform.provider.model.UsageRecordView;
import io.cranial.platform.provider.model.UsageSummaryView;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class UsageTrackingService {

    private static final Logger log = LoggerFactory.getLogger(UsageTrackingService.class);

    private final UsageRecordMapper usageRecordMapper;
    private final MeterRegistry meterRegistry;

    public UsageTrackingService(UsageRecordMapper usageRecordMapper, MeterRegistry meterRegistry) {
        this.usageRecordMapper = usageRecordMapper;
        this.meterRegistry = meterRegistry;
    }

    public void recordUsage(
            UUID applicationId,
            UUID tenantId,
            String correlationId,
            ProviderEntity provider,
            ModelEntity model,
            String capabilityCode,
            String taskCode,
            int promptTokens,
            int completionTokens,
            long durationMs,
            String status,
            String errorMessage
    ) {
        int totalTokens = promptTokens + completionTokens;
        boolean isLocal = model != null && Boolean.TRUE.equals(model.getIsLocal());

        int estimatedCostCents = 0;
        if (model != null && !isLocal) {
            int inputCost = model.getCostPerMillionInputCents() != null ? model.getCostPerMillionInputCents() : 0;
            int outputCost = model.getCostPerMillionOutputCents() != null ? model.getCostPerMillionOutputCents() : 0;
            long rawCents = ((long) promptTokens * inputCost + (long) completionTokens * outputCost) / 1_000_000L;
            estimatedCostCents = (int) rawCents;
        }

        UsageRecordEntity entity = new UsageRecordEntity();
        entity.setId(UUID.randomUUID());
        entity.setApplicationId(applicationId);
        entity.setTenantId(tenantId);
        entity.setCorrelationId(correlationId != null ? correlationId : UUID.randomUUID().toString());
        entity.setProviderId(provider != null ? provider.getId() : (model != null ? model.getProviderId() : UUID.randomUUID()));
        entity.setModelId(model != null ? model.getId() : UUID.randomUUID());
        entity.setCapabilityCode(capabilityCode);
        entity.setTaskCode(taskCode);
        entity.setPromptTokens(promptTokens);
        entity.setCompletionTokens(completionTokens);
        entity.setTotalTokens(totalTokens);
        entity.setDurationMs(durationMs);
        entity.setIsLocal(isLocal);
        entity.setEstimatedCostCents(estimatedCostCents);
        entity.setStatus(status);
        entity.setErrorMessage(errorMessage);
        entity.setCreatedAt(Instant.now());

        try {
            usageRecordMapper.insert(entity);
        } catch (Exception ex) {
            log.error("Failed to insert AI usage record: {}", ex.getMessage());
        }

        // Record metrics
        String providerCode = provider != null ? provider.getCode() : "unknown";
        String modelIdent = model != null ? model.getModelIdentifier() : "unknown";

        meterRegistry.counter("cranial.model.requests",
                "provider", providerCode,
                "model", modelIdent,
                "status", status,
                "is_local", String.valueOf(isLocal)
        ).increment();

        if (totalTokens > 0) {
            meterRegistry.counter("cranial.model.tokens",
                    "provider", providerCode,
                    "model", modelIdent,
                    "type", "prompt"
            ).increment(promptTokens);

            meterRegistry.counter("cranial.model.tokens",
                    "provider", providerCode,
                    "model", modelIdent,
                    "type", "completion"
            ).increment(completionTokens);
        }

        if (durationMs > 0) {
            meterRegistry.timer("cranial.model.duration",
                    "provider", providerCode,
                    "model", modelIdent
            ).record(durationMs, TimeUnit.MILLISECONDS);
        }
    }

    public UsageSummaryView getSummary(UUID applicationId) {
        Map<String, Object> row = usageRecordMapper.getUsageSummary(applicationId);
        if (row == null || row.isEmpty()) {
            return new UsageSummaryView(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }

        List<UsageRecordView> recent = listRecent(applicationId, 100);
        long p50 = 0;
        long p95 = 0;
        long p99 = 0;
        if (!recent.isEmpty()) {
            List<Long> durations = recent.stream().map(UsageRecordView::durationMs).sorted().toList();
            p50 = durations.get((int) (durations.size() * 0.5));
            p95 = durations.get((int) Math.min(durations.size() - 1, durations.size() * 0.95));
            p99 = durations.get((int) Math.min(durations.size() - 1, durations.size() * 0.99));
        }

        return new UsageSummaryView(
                extract(row, "total_requests"),
                extract(row, "local_requests"),
                extract(row, "hosted_requests"),
                extract(row, "total_prompt_tokens"),
                extract(row, "total_completion_tokens"),
                extract(row, "total_tokens"),
                extract(row, "total_cost_cents"),
                extract(row, "fallback_blocked_count"),
                p50,
                p95,
                p99
        );
    }

    private long extract(Map<String, Object> row, String key) {
        String clean = key.replace("_", "").toLowerCase();
        for (Map.Entry<String, Object> e : row.entrySet()) {
            String k = e.getKey().replace("_", "").toLowerCase();
            if (k.equals(clean)) {
                return toLong(e.getValue());
            }
        }
        return 0L;
    }

    public List<UsageRecordView> listRecent(UUID applicationId, int limit) {
        return usageRecordMapper.selectList(null).stream()
                .filter(r -> applicationId == null || applicationId.equals(r.getApplicationId()))
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .limit(limit)
                .map(this::toView)
                .toList();
    }

    private UsageRecordView toView(UsageRecordEntity r) {
        return new UsageRecordView(
                r.getId(),
                r.getApplicationId(),
                r.getCorrelationId(),
                r.getProviderId(),
                r.getModelId(),
                r.getCapabilityCode(),
                r.getTaskCode(),
                r.getPromptTokens() != null ? r.getPromptTokens() : 0,
                r.getCompletionTokens() != null ? r.getCompletionTokens() : 0,
                r.getTotalTokens() != null ? r.getTotalTokens() : 0,
                r.getDurationMs() != null ? r.getDurationMs() : 0L,
                Boolean.TRUE.equals(r.getIsLocal()),
                r.getEstimatedCostCents() != null ? r.getEstimatedCostCents() : 0,
                r.getStatus(),
                r.getErrorMessage(),
                r.getCreatedAt()
        );
    }

    private long toLong(Object obj) {
        if (obj == null) return 0L;
        if (obj instanceof Number num) return num.longValue();
        try {
            return Long.parseLong(obj.toString());
        } catch (Exception ex) {
            return 0L;
        }
    }
}
