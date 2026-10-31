package io.cranial.platform.analytics.engine;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cranial.platform.analytics.entity.AnalyticsQueryCacheEntity;
import io.cranial.platform.analytics.mapper.AnalyticsQueryCacheMapper;
import io.cranial.platform.analytics.model.AnalyticsQueryRequest;
import io.cranial.platform.analytics.model.AnalyticsReportResult;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class AnalyticsCacheManager {

    private final AnalyticsQueryCacheMapper cacheMapper;
    private final ObjectMapper objectMapper;

    public AnalyticsCacheManager(AnalyticsQueryCacheMapper cacheMapper, ObjectMapper objectMapper) {
        this.cacheMapper = cacheMapper;
        this.objectMapper = objectMapper;
    }

    public String computeQueryHash(String datasetCode, AnalyticsQueryRequest request, UUID applicationId, UUID tenantId) {
        try {
            Map<String, Object> canonical = new LinkedHashMap<>();
            canonical.put("dataset_code", datasetCode != null ? datasetCode : "");
            canonical.put("application_id", applicationId != null ? applicationId.toString() : "");
            canonical.put("tenant_id", tenantId != null ? tenantId.toString() : "");
            canonical.put("nl_query", request != null && request.naturalLanguageQuery() != null ? request.naturalLanguageQuery().trim().toLowerCase() : "");
            canonical.put("time_range", request != null && request.timeRange() != null ? request.timeRange() : "");
            canonical.put("start_date", request != null && request.startDate() != null ? request.startDate() : "");
            canonical.put("end_date", request != null && request.endDate() != null ? request.endDate() : "");
            canonical.put("granularity", request != null && request.granularity() != null ? request.granularity() : "");
            canonical.put("metrics", request != null && request.metrics() != null ? request.metrics() : "");
            canonical.put("group_by", request != null && request.groupBy() != null ? request.groupBy() : "");
            canonical.put("detect_anomalies", request != null && Boolean.TRUE.equals(request.detectAnomalies()));
            canonical.put("include_forecast", request != null && Boolean.TRUE.equals(request.includeForecast()));
            String json = objectMapper.writeValueAsString(canonical);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(json.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return UUID.randomUUID().toString().replace("-", "");
        }
    }

    public Optional<AnalyticsReportResult> getCached(String queryHash) {
        try {
            AnalyticsQueryCacheEntity entity = cacheMapper.selectOne(
                    new LambdaQueryWrapper<AnalyticsQueryCacheEntity>()
                            .eq(AnalyticsQueryCacheEntity::getQueryHash, queryHash)
            );
            if (entity == null) {
                return Optional.empty();
            }

            Instant now = Instant.now();
            if (entity.getExpiresAt() != null && entity.getExpiresAt().isBefore(now)) {
                cacheMapper.deleteById(entity.getId());
                return Optional.empty();
            }

            entity.setHitCount(entity.getHitCount() != null ? entity.getHitCount() + 1 : 1);
            entity.setLastAccessedAt(now);
            cacheMapper.updateById(entity);

            AnalyticsReportResult cachedResult = objectMapper.readValue(entity.getResultPayloadJson(), AnalyticsReportResult.class);
            // Return marked as cached
            return Optional.of(new AnalyticsReportResult(
                    cachedResult.reportId(),
                    cachedResult.datasetCode(),
                    cachedResult.title(),
                    cachedResult.timeRange(),
                    cachedResult.timezone(),
                    cachedResult.sourceFreshnessAt(),
                    cachedResult.dataCompletenessRatio(),
                    true, // cached = true
                    cachedResult.kpis(),
                    cachedResult.timeSeries(),
                    cachedResult.breakdown(),
                    cachedResult.tabularRows(),
                    cachedResult.anomalies(),
                    cachedResult.forecast(),
                    cachedResult.provenance(),
                    cachedResult.naturalLanguageSummary(),
                    cachedResult.clarification(),
                    cachedResult.dataQualityWarnings(),
                    cachedResult.durationMs()
            ));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public void putCache(String queryHash, String datasetCode, UUID applicationId, AnalyticsQueryRequest req, AnalyticsReportResult result, Duration ttl) {
        try {
            Instant now = Instant.now();
            AnalyticsQueryCacheEntity entity = new AnalyticsQueryCacheEntity();
            entity.setId(UUID.randomUUID());
            entity.setQueryHash(queryHash);
            entity.setApplicationId(applicationId);
            entity.setDatasetCode(datasetCode);
            entity.setQueryPayloadJson(objectMapper.writeValueAsString(req));
            entity.setResultPayloadJson(objectMapper.writeValueAsString(result));
            entity.setHitCount(1);
            entity.setExpiresAt(now.plus(ttl != null ? ttl : Duration.ofMinutes(5)));
            entity.setCreatedAt(now);
            entity.setLastAccessedAt(now);

            cacheMapper.insert(entity);
        } catch (Exception ignored) {
            // non-fatal
        }
    }

    public int purgeCache(String datasetCode, UUID applicationId) {
        LambdaQueryWrapper<AnalyticsQueryCacheEntity> wrapper = new LambdaQueryWrapper<>();
        if (datasetCode != null && !datasetCode.isBlank()) {
            wrapper.eq(AnalyticsQueryCacheEntity::getDatasetCode, datasetCode.trim());
        }
        if (applicationId != null) {
            wrapper.eq(AnalyticsQueryCacheEntity::getApplicationId, applicationId);
        }
        return cacheMapper.delete(wrapper);
    }

    public Map<String, Object> getCacheStats() {
        Long total = cacheMapper.selectCount(null);
        return Map.of(
                "total_cached_queries", total != null ? total : 0,
                "cache_engine", "SQL_BACKED_DETERMINISTIC_CACHE",
                "default_ttl_seconds", 300
        );
    }
}

