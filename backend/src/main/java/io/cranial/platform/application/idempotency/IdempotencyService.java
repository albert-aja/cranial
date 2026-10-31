package io.cranial.platform.application.idempotency;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cranial.platform.platform.crypto.CryptoService;
import io.cranial.platform.platform.exception.CranialException;
import io.cranial.platform.platform.exception.ErrorCode;
import io.cranial.platform.platform.observability.PlatformMetrics;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;

@Service
public class IdempotencyService {

    private static final Duration DEFAULT_TTL = Duration.ofHours(24);

    private final IdempotencyRecordMapper mapper;
    private final CryptoService cryptoService;
    private final ObjectMapper objectMapper;
    private final PlatformMetrics metrics;

    public IdempotencyService(
            IdempotencyRecordMapper mapper,
            CryptoService cryptoService,
            ObjectMapper objectMapper,
            PlatformMetrics metrics
    ) {
        this.mapper = mapper;
        this.cryptoService = cryptoService;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
    }

    public record IdempotentExecution<T>(T body, boolean replayed) {
    }

    @Transactional
    public <T> IdempotentExecution<T> execute(
            UUID applicationId,
            String applicationCode,
            String idempotencyKey,
            String endpoint,
            Object canonicalPayload,
            Class<T> responseType,
            Supplier<T> supplier
    ) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return new IdempotentExecution<>(supplier.get(), false);
        }
        String trimmedKey = idempotencyKey.trim();
        if (trimmedKey.length() > 160) {
            throw new CranialException(ErrorCode.VALIDATION_ERROR, "Idempotency-Key must not exceed 160 characters");
        }

        String requestHash = hashPayload(canonicalPayload);
        Instant now = Instant.now();

        IdempotencyRecordEntity existing = mapper.selectOne(
                new LambdaQueryWrapper<IdempotencyRecordEntity>()
                        .eq(IdempotencyRecordEntity::getApplicationId, applicationId)
                        .eq(IdempotencyRecordEntity::getIdempotencyKey, trimmedKey)
                        .eq(IdempotencyRecordEntity::getEndpoint, endpoint)
        );

        if (existing != null) {
            if (existing.getExpiresAt().isBefore(now)) {
                mapper.deleteById(existing.getId());
            } else {
                if (!cryptoService.constantTimeEquals(existing.getRequestHash(), requestHash)) {
                    throw new CranialException(
                            ErrorCode.IDEMPOTENCY_KEY_CONFLICT,
                            "Idempotency-Key was already used with a different request payload"
                    );
                }
                metrics.recordIdempotencyHit(applicationCode);
                try {
                    T cached = objectMapper.readValue(existing.getResponseBody(), responseType);
                    return new IdempotentExecution<>(cached, true);
                } catch (Exception ex) {
                    throw new CranialException(ErrorCode.INTERNAL_ERROR, "Failed to deserialize cached idempotent response");
                }
            }
        }

        T freshResponse = supplier.get();
        try {
            IdempotencyRecordEntity record = new IdempotencyRecordEntity();
            record.setId(UUID.randomUUID());
            record.setApplicationId(applicationId);
            record.setIdempotencyKey(trimmedKey);
            record.setEndpoint(endpoint);
            record.setRequestHash(requestHash);
            record.setResponseStatus(200);
            record.setResponseBody(objectMapper.writeValueAsString(freshResponse));
            record.setExpiresAt(now.plus(DEFAULT_TTL));
            record.setCreatedAt(now);
            mapper.insert(record);
        } catch (Exception ex) {
            throw new CranialException(ErrorCode.INTERNAL_ERROR, "Failed to store idempotency record");
        }

        return new IdempotentExecution<>(freshResponse, false);
    }

    private String hashPayload(Object payload) {
        try {
            String canonical = objectMapper.writeValueAsString(payload);
            return cryptoService.sha256Hex(canonical);
        } catch (Exception ex) {
            throw new CranialException(ErrorCode.INTERNAL_ERROR, "Failed to compute canonical request hash");
        }
    }
}

