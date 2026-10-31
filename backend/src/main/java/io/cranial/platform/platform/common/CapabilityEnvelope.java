package io.cranial.platform.platform.common;

import io.cranial.platform.platform.observability.CorrelationIdHolder;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record CapabilityEnvelope<T>(
        String requestId,
        String correlationId,
        String applicationId,
        String capability,
        String task,
        String status,
        T data,
        ValidationSummary validation,
        List<String> warnings,
        List<ErrorDetail> errors,
        Map<String, Object> metadata
) {
    public record ValidationSummary(boolean schemaValid, String schemaVersion) {
    }

    public static <T> CapabilityEnvelope<T> completed(
            String requestId,
            String applicationId,
            String capability,
            String task,
            String schemaVersion,
            T data,
            List<String> warnings,
            Map<String, Object> metadata
    ) {
        String effectiveReqId = (requestId != null && !requestId.isBlank()) ? requestId : UUID.randomUUID().toString();
        return new CapabilityEnvelope<>(
                effectiveReqId,
                CorrelationIdHolder.currentOrGenerate(),
                applicationId,
                capability,
                task,
                "completed",
                data,
                new ValidationSummary(true, schemaVersion),
                warnings != null ? warnings : List.of(),
                List.of(),
                metadata != null ? metadata : Map.of()
        );
    }
}

